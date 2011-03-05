/*
 * Java Payloads.
 * 
 * Copyright (c) 2010, 2011 Michael 'mihi' Schierl
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *   
 * - Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *   
 * - Neither name of the copyright holders nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *   
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND THE CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR THE CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package javapayload.test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.regex.Pattern;

import javapayload.handler.stager.StagerHandler;
import javapayload.loader.StandaloneLoader;

public class StageTest {
	public static void main(String[] args) throws Exception {
		File baseDir = new File(StageTest.class.getResource("stagetests").toURI());
		if (args.length == 1) {
			File file = new File(baseDir, args[0]+".txt");
			testStage(file, "LocalTest", new OutputStreamWriter(System.out));
		} else {
			System.out.println("Testing stages (LocalTest)...");
			File[] files = baseDir.listFiles();
			for (int i = 0; i < files.length; i++) {
				testStage(files[i], "LocalTest");
			}
			System.out.println("Testing stages (BindTCP)...");
			final Throwable[] tt = new Throwable[1];
			Thread th = new Thread(new Runnable() {
				public void run() {
					try {
						StandaloneLoader.main(new String[] {"BindMultiTCP", "localhost", "60123"});
					} catch (Throwable t) {
						tt[0] = t;
					}
				};
			});
			th.start();
			for (int i = 0; i < files.length; i++) {
				testStage(files[i], "BindTCP localhost 60123");
			}
			StagerHandler.main("BindTCP localhost 60123 -- StopListening".split(" "));
			StagerHandler.main("BindTCP localhost 60123 -- StopListening".split(" "));
			th.join();
			if (tt[0] != null)
					throw new Exception("Stager died", tt[0]);
			new File("jsh.txt").delete();
			System.out.println("Stage tests finished.");
			new ThreadWatchdogThread(5000).start();
		}
	}

	private static void testStage(File file, String stager) throws Exception {
		if (!file.getName().endsWith(".txt"))
			return;
		StringWriter sw = new StringWriter();
		Pattern regex = testStage(file, stager, sw);
		String output = sw.toString().replaceAll("\r\n", "\n").replaceAll("\r", "\n");
		if (!regex.matcher(output).matches()) {
			System.err.println("Pattern:\r\n"+regex.pattern());
			System.err.println("Output:\r\n"+output);
			throw new Exception("Output did not match");
		}
	}
	
	private static Pattern testStage(File file, String stager, Writer output) throws Exception {
		BufferedReader desc = new BufferedReader(new FileReader(file));
		System.out.println("\t"+file.getName().replaceAll("\\.txt", ""));
		String stage = desc.readLine();
		StringBuffer sb = new StringBuffer();
		String delimiter = desc.readLine();
		String line;
		while ((line = desc.readLine()) != null) {
			if (line.equals(delimiter)) break;
			sb.append(line).append("\r\n");
		}
		// TODO remove and fix this when SendParameters is merged;
		// currently stages with parameters cannot be tested with
		// the BindMultiTCP stager
		if (stage.contains(" ") && !stager.equals("LocalTest"))
			return Pattern.compile("");
		String[] args = (stager + " -- "+stage).split(" ");
		StagerHandler.Loader loader = new StagerHandler.Loader(args);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(baos);		
		loader.stageHandler.consoleErr = out;
		loader.stageHandler.consoleOut = out;
		loader.stageHandler.consoleIn = new BlockingInputStream(new ByteArrayInputStream(sb.toString().getBytes()));
		loader.handle(System.err, null);
		sb.setLength(0);
		while ((line = desc.readLine()) != null) {
			if (line.equals(delimiter)) break;
			sb.append(line).append("\n");
		}
		while(sb.charAt(sb.length()-1) == '\n')
			sb.setLength(sb.length()-1);
		Thread.sleep(500);
		out.flush();
		String outputStr = new String(baos.toByteArray());
		while (outputStr.endsWith("\n") || outputStr.endsWith("\r"))
			outputStr = outputStr.substring(0, outputStr.length()-1);
		output.write(outputStr);
		return Pattern.compile(sb.toString());
	}
}