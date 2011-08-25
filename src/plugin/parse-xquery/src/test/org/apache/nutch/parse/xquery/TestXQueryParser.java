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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.TestCase;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.metadata.Metadata;
import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseResult;
import org.apache.nutch.parse.ParseUtil;
import org.apache.nutch.protocol.Content;
import org.apache.nutch.util.NutchConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;

/**
 * @author daniel
 *
 */
public class TestXQueryParser extends TestCase {

	// This system property is defined in ./src/plugin/build-plugin.xml
	private String sampleDir = System.getProperty("test.data", ".");
	private String fileSeparator = System.getProperty("file.separator");

	public TestXQueryParser(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}
	
	private DocumentFragment readHtmlDocument(InputStream is) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(is);
		DocumentFragment fragment = document.createDocumentFragment();
		fragment.appendChild(document.getDocumentElement());
		return fragment;
	}
	
	public void testIt() throws Exception {
		String url = "http://test.org/";
	    String contentType = "text/html";
	    File file = new File(sampleDir, "kenmore-microwave-17in.html");
	    FileInputStream is = new FileInputStream(file);
	    byte bytes[] = new byte[(int) file.length()];
	    is.read(bytes);
	    Configuration conf = NutchConfiguration.create();
	    conf.writeXml(System.out);
	    Content content =
	    	      new Content(url, url, bytes, contentType, new Metadata(), conf);
	    Parse parse =  new ParseUtil(conf).parse(content).get(content.getUrl());

	}

	public void testIt2() throws Exception {
		FileInputStream is = new FileInputStream(sampleDir + fileSeparator + "kenmore-microwave-17in.html");
		DocumentFragment doc = this.readHtmlDocument(is);
		Content content = null;
		ParseResult parseResult = null;
		HTMLMetaTags metaTags = null;
		XQueryParser xqp = new XQueryParser();
		xqp.filter(content, parseResult, metaTags, doc);
	}
}
