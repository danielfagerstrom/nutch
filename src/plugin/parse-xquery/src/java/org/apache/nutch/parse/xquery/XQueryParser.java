/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.parse.xquery;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xquery.XQConnection;
import javax.xml.xquery.XQConstants;
import javax.xml.xquery.XQDataSource;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQPreparedExpression;
import javax.xml.xquery.XQSequence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.MapFile.Reader;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.MapFileOutputFormat;
import org.apache.nutch.metadata.Metadata;
import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.HtmlParseFilter;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseResult;
import org.apache.nutch.parse.ParseUtil;
import org.apache.nutch.protocol.Content;
import org.apache.nutch.util.NutchConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Daniel Fagerstrom
 *
 */
public class XQueryParser implements HtmlParseFilter {
	public static final String XQUERYPARSER_RULES_FILE = "xqueryparser.rules.file";
	public static final String METADATA_FIELD = "xquery-parser";
	private static final String DEFAULT_XQUERY_DATA_SOURCE = "net.sf.saxon.xqj.SaxonXQDataSource";
	// private static final String DEFAULT_XQUERY_DATA_SOURCE = "ch.ethz.mxquery.xqj.MXQueryXQDataSource";
	/** My logger */
	private final static Log LOG = LogFactory.getLog(XQueryParser.class);
	private XQConnection xqConnection = null;
	private Configuration conf;
	private Map<String,List<PatternQueryPair>> rules = new HashMap<String, List<PatternQueryPair>>();
	
	private class PatternQueryPair {
		public Pattern pattern;
		public XQPreparedExpression expr;
		public PatternQueryPair(Pattern pattern, XQPreparedExpression expr) {
			this.pattern = pattern;
			this.expr = expr;
		}		
	}

	public XQueryParser() {
		try {
			Class<?> xqDSClass = Class.forName(DEFAULT_XQUERY_DATA_SOURCE);
			XQDataSource ds = (XQDataSource) xqDSClass.newInstance();
			this.xqConnection = ds.getConnection();
		} catch (Exception e) {
			if (LOG.isErrorEnabled()) { LOG.error(e.getMessage()); }
			throw new RuntimeException(e.getMessage(), e);      
		}
	}

	/* (non-Javadoc)
	 * @see org.apache.nutch.parse.HtmlParseFilter#filter(org.apache.nutch.protocol.Content, org.apache.nutch.parse.ParseResult, org.apache.nutch.parse.HTMLMetaTags, org.w3c.dom.DocumentFragment)
	 */
	@Override
	public ParseResult filter(Content content, ParseResult parseResult,
			HTMLMetaTags metaTags, DocumentFragment doc) {
		String urlStr = content.getUrl();
		try {
			XQPreparedExpression expr = this.matchURL(urlStr);
			if (expr != null) {
				String parseOutput;
				parseOutput = this.parseDOM(expr, urlStr, doc);
			    // get parse obj
			    Parse parse = parseResult.get(urlStr);
			    Metadata metadata = parse.getData().getParseMeta();
			    if (parseOutput != null) {
			    	metadata.add(METADATA_FIELD, parseOutput);
			    }
			}
		} catch (Exception e) {
			if (LOG.isErrorEnabled()) { LOG.error(e.getMessage()); }
			e.printStackTrace();
			throw new RuntimeException(e.getMessage(), e);      
		}

		return parseResult;
	}

	public void setConf(Configuration conf) {
		this.conf = conf;
		try {
			this.parseRules();
		} catch (Exception e) {
			if (LOG.isErrorEnabled()) { LOG.error(e.getMessage()); }
			e.printStackTrace();
			throw new RuntimeException(e.getMessage(), e);      
		}
	}

	@Override
	public Configuration getConf() {
		return this.conf;
	}

	private Document readXMLDocument(InputStream is) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(is);
		return document;
	}
	
	private void parseRules() throws Exception {
		String rulesFileName = this.getConf().get(XQUERYPARSER_RULES_FILE);
		URL rulesResource = this.getConf().getResource(rulesFileName);
	    InputStream is = rulesResource.openStream();
		Document document = this.readXMLDocument(is);
		Element root = document.getDocumentElement();
		if (!"parse-rules".equals(root.getTagName())) {
			if (LOG.isErrorEnabled()) { LOG.error("No parse-rules element."); }
		}
		NodeList rules = root.getChildNodes();
		for (int i = 0; i < rules.getLength(); i++) {
			Node ruleNode = rules.item(i);
			if (!(ruleNode instanceof Element))
				continue;
			Element rule = (Element) ruleNode;
			if (!"rule".equals(rule.getTagName())) {
				if (LOG.isErrorEnabled()) { LOG.error("Non rule element."); }
			}
			String domain = rule.getAttribute("domain");
			String patternStr = rule.getAttribute("pattern");
			String xquery = rule.getAttribute("xquery");
			if (!this.rules.containsKey(domain))
				this.rules.put(domain, new LinkedList<PatternQueryPair>());
			URL resolvedXQueryPath = rulesResource.toURI().resolve(xquery).toURL();
			InputStream xqis = resolvedXQueryPath.openStream();
			XQPreparedExpression expr = this.xqConnection.prepareExpression(xqis);
			Pattern pattern = Pattern.compile(patternStr);
			this.rules.get(domain).add(new PatternQueryPair(pattern, expr));

			if (LOG.isDebugEnabled()) { LOG.debug("Parse rule: " + domain + " " + patternStr + " " + xquery); }
		}
	}
	
	public XQPreparedExpression matchURL(String urlStr) throws MalformedURLException {
		URL url = new URL(urlStr);
		String domain = url.getHost();
		String path = url.getPath();
		LinkedList<PatternQueryPair> ruleList = (LinkedList<PatternQueryPair>) this.rules.get(domain);
		if (null != ruleList) {
			for (PatternQueryPair pair: ruleList) {
				Matcher matcher = pair.pattern.matcher(path);
				if (matcher.matches())
					return pair.expr;
			}
		}
		return null;
	}
	
	public String parseDOM(XQPreparedExpression expr, String urlStr, DocumentFragment doc) throws XQException {
		expr.bindNode(XQConstants.CONTEXT_ITEM, doc, null);
		QName[] externalVariables = expr.getAllExternalVariables();
		for (QName qName: externalVariables)
			if ("url".equals(qName.getLocalPart()))
				expr.bindString(new QName("url"), urlStr, null);
		XQSequence sequence = expr.executeQuery();
		String parseOutput = sequence.getSequenceAsString(null);
		return parseOutput;
	}
	
	public void printParseRules() {
		for (Entry<String, List<PatternQueryPair>> domain : this.rules.entrySet()) {
			System.out.println(domain.getKey());
			for (PatternQueryPair pair: domain.getValue()) {
				System.out.println("\t" + pair.pattern);
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
	    Configuration conf = NutchConfiguration.create();
	    Content content = null;
		if (args.length < 1) {
			usage();
			return;
		}
		String urlStr = args[0];
		String segment = null;
		if (args.length == 2) {
			segment = args[1];
		}
		if (segment != null) {
			Path file = new Path(segment, Content.DIR_NAME);
			FileSystem fs = FileSystem.get(conf);
			System.out.println("path: " + file.toString());
			Reader[] readers = MapFileOutputFormat.getReaders(fs, file, conf);
			content = new Content();
			for (Reader reader: readers) {
				if (reader.get(new Text(urlStr), content) != null)
					continue;
			}
			for (Reader reader: readers)
				reader.close();
		} else {
			content = createContent(conf, urlStr);			
		}
	    Parse parse =  new ParseUtil(conf).parse(content).get(content.getUrl());
	    String result = parse.getData().getMeta(XQueryParser.METADATA_FIELD);
	    System.out.println(result);
	}

	private static Content createContent(Configuration conf, String urlStr)
			throws FileNotFoundException, IOException {
	    String contentType = "text/html";
	    URL url = new URL(urlStr);
	    URLConnection connection = url.openConnection();
	    InputStream is = connection.getInputStream();
	    byte bytes[] = new byte[(int) connection.getContentLength()];
	    is.read(bytes);
	    Content content = new Content(urlStr, urlStr, bytes, contentType, new Metadata(), conf);
		return content;
	}

	private static void usage() {
	    System.err.println("Usage: XQueryParser <url> [segment]\n");		
	}
}
