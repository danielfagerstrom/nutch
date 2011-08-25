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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.xml.namespace.QName;
import javax.xml.xquery.XQConnection;
import javax.xml.xquery.XQConstants;
import javax.xml.xquery.XQDataSource;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQPreparedExpression;
import javax.xml.xquery.XQSequence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.metadata.Metadata;
import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.HtmlParseFilter;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseResult;
import org.apache.nutch.protocol.Content;
import org.w3c.dom.DocumentFragment;

/**
 * @author Daniel Fagerstrom
 *
 */
public class XQueryParser implements HtmlParseFilter {
	private static final String XQUERY_PARSER_RESULT = "xquery-parser";
	private static final String DEFAULT_XQUERY_DATA_SOURCE = "net.sf.saxon.xqj.SaxonXQDataSource";
	// private static final String DEFAULT_XQUERY_DATA_SOURCE = "ch.ethz.mxquery.xqj.MXQueryXQDataSource";
	/** My logger */
	private final static Log LOG = LogFactory.getLog(XQueryParser.class);
	private Configuration conf;
	private XQConnection xqConnection = null;

	public XQueryParser() {
		try {
			Class xqDSClass = Class.forName(DEFAULT_XQUERY_DATA_SOURCE);
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

		String parseOutput = null;
		try {
			FileInputStream is = new FileInputStream("microdata.xq");
			XQPreparedExpression expr = this.xqConnection.prepareExpression(is);
			expr.bindNode(XQConstants.CONTEXT_ITEM, doc, null);
			// expr.bindDocument(new QName("doc"), docStream, null, null);
			XQSequence sequence = expr.executeQuery();
			parseOutput = sequence.getSequenceAsString(null);
		} catch (Exception e) {
			e.printStackTrace(System.err);
			if (LOG.isErrorEnabled()) { LOG.error(e.getMessage()); }
			throw new RuntimeException(e.getMessage(), e);      
		}
	    // get parse obj
	    Parse parse = parseResult.get(content.getUrl());
	    Metadata metadata = parse.getData().getParseMeta();
	    if (parseOutput != null) {
	    	metadata.add(XQUERY_PARSER_RESULT, parseOutput);
	    }

		return parseResult;
	}

	public void setConf(Configuration conf) {
		this.conf = conf;
	}

	public Configuration getConf() {
		return this.conf;
	}
	
	public void test(InputStream is) throws XQException {
		XQPreparedExpression expr = this.xqConnection.prepareExpression(is);
		XQSequence sequence = expr.executeQuery();
		sequence.writeSequence(System.out, null);
		this.xqConnection.close();		
	}
	
	public void test() throws FileNotFoundException, XQException {
		FileInputStream fis = new FileInputStream("microdata.xq");
		XQueryParser xqp = new XQueryParser();
		xqp.test(fis);
	}

	/**
	 * @param args
	 * @throws XQException 
	 */
	public static void main(String[] args) throws XQException {
		XQueryParser xqp = new XQueryParser();
		//xqp.test();
	}

}
