/*******************************************************************************
 * Copyright (c) Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.json.benchmark;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Json Benchmark for trace event traces
 *
 * @author Matthew Khouzam
 */
public class JsonBenchmark {
	interface ITraceEventConstants {

		/**
		 * Timestamp field name
		 */
		String TIMESTAMP = "ts"; //$NON-NLS-1$
		/**
		 * Duration field name
		 */
		String DURATION = "dur"; //$NON-NLS-1$
		/**
		 * Name field name
		 */
		String NAME = "name"; //$NON-NLS-1$
		/**
		 * TID field name
		 */
		String TID = "tid"; //$NON-NLS-1$
		/**
		 * PID field name
		 */
		String PID = "pid"; //$NON-NLS-1$
		/**
		 * Phase field name
		 */
		String PHASE = "ph"; //$NON-NLS-1$
		/**
		 * Category field name
		 */
		String CATEGORY = "cat"; //$NON-NLS-1$
		/**
		 * Id field name
		 */
		String ID = "id"; //$NON-NLS-1$
		/**
		 * Arguments field name
		 */
		String ARGS = "args"; //$NON-NLS-1$

	}

	private static final double MICRO_TO_NANO = 1000.0;

	public static String readNextEventString(BufferedReader parser) throws IOException {
		StringBuffer sb = new StringBuffer();
		int scope = -1;
		int arrScope = 0;
		boolean inQuotes = false;
		char elem = (char) parser.read();
		while (elem != (char) -1) {
			if (elem == '"') {
				inQuotes = !inQuotes;
			} else {
				if (inQuotes) {
					// do nothing
				} else if (elem == '[') {
					arrScope++;
				} else if (elem == ']') {
					if (arrScope > 0) {
						arrScope--;
					} else {
						return null;
					}
				} else if (elem == '{') {
					scope++;
				} else if (elem == '}') {
					if (scope > 0) {
						scope--;
					} else {
						sb.append(elem);
						return sb.toString();
					}
				}
			}
			if (scope >= 0) {
				sb.append(elem);
			}
			elem = (char) parser.read();
		}
		return null;
	}

	public static void main(String args[]) throws IOException {
		File f = new File("/home/matthew/trace/trace.json");
		long t0, t1;
		// be cache hot for both
		try (BufferedReader br = new BufferedReader(new FileReader(f))) {
			String line = readNextEventString(br);
			while (line != null) {
				parseJson(line);
				line = readNextEventString(br);
			}
		}
		// Test org.json.simple
		{
			t0 = System.nanoTime();
			try (BufferedReader br = new BufferedReader(new FileReader(f))) {
				String line = readNextEventString(br);
				while (line != null) {
					parseJsonSimple(line);
					line = readNextEventString(br);
				}
			} finally {
				t1 = System.nanoTime();
			}
			System.out.println("org.json.simple Time " + (t1 - t0) * 0.000000001);
		}
		// Test org.json
		{
			t0 = System.nanoTime();
			try (BufferedReader br = new BufferedReader(new FileReader(f))) {
				String line = readNextEventString(br);
				while (line != null) {
					parseJson(line);
					line = readNextEventString(br);
				}
			} finally {
				t1 = System.nanoTime();
			}
			System.out.println("org.json Time " + (t1 - t0) * 0.000000001);
		}
	}

	/**
	 * Parse a JSON string
	 *
	 * @param fieldsString
	 *            the string
	 * @return an event field
	 */
	public static boolean parseJson(String fieldsString) {
		// looks like this
		// {"ts":94824347413117,"phase":"B","tid":39,"name":"TimeGraphView:BuildThread","args"={"trace":"django-httpd"}}
		org.json.JSONObject root;
		Map<String, Object> argsMap = new HashMap<>();
		try {
			root = new org.json.JSONObject(fieldsString);
			long ts = 0;

			Double tso = root.optDouble(ITraceEventConstants.TIMESTAMP);
			if (Double.isFinite(tso)) {
				ts = (long) (tso * MICRO_TO_NANO);
			}
			char phase = root.optString(ITraceEventConstants.PHASE, "I").charAt(0); //$NON-NLS-1$
			String name = String.valueOf(root.optString(ITraceEventConstants.NAME, 'E' == phase ? "exit" : "unknown")); //$NON-NLS-1$ //$NON-NLS-2$
			Integer tid = root.optInt(ITraceEventConstants.TID);
			if (tid == Integer.MIN_VALUE) {
				tid = null;
			}
			Object pid = root.opt(ITraceEventConstants.PID);
			Double duration = root.optDouble(ITraceEventConstants.DURATION);
			if (Double.isFinite(duration)) {
				duration = (duration * MICRO_TO_NANO);
			}
			String category = root.optString(ITraceEventConstants.CATEGORY);
			String id = root.optString(ITraceEventConstants.ID);
			org.json.JSONObject args = root.optJSONObject(ITraceEventConstants.ARGS);
			if (args != null) {
				Set<String> keys = args.keySet();
				for (String key : keys) {
					String value = args.optString(key);
					argsMap.put("arg/" + key, String.valueOf(value));
				}
			}
			argsMap.put(ITraceEventConstants.TIMESTAMP, ts);
			argsMap.put(ITraceEventConstants.PHASE, phase);
			argsMap.put(ITraceEventConstants.NAME, name);
			if (tid != null) {
				argsMap.put(ITraceEventConstants.TID, tid);
			}
			if (pid != null) {
				argsMap.put(ITraceEventConstants.PID, pid);
			}
			if (Double.isFinite(duration)) {
				argsMap.put(ITraceEventConstants.DURATION, duration);
			}
			return true;
		} catch (JSONException e1) {
			// invalid, return null and it will fail
		}
		return false;
	}

	private static org.json.simple.parser.JSONParser parser = new JSONParser();

	/**
	 * Parse a JSON string
	 *
	 * @param fieldsString
	 *            the string
	 * @return an event field
	 */
	public static boolean parseJsonSimple(String fieldsString) {
		// looks like this
		// {"ts":94824347413117,"phase":"B","tid":39,"name":"TimeGraphView:BuildThread","args"={"trace":"django-httpd"}}
		try {
			Map root = (Map) parser.parse(fieldsString);
			Map<String, Object> argsMap = new HashMap<>();

			long ts = 0;

			Double tso = (Double) root.getOrDefault(ITraceEventConstants.TIMESTAMP, Double.NaN);
			if (Double.isFinite(tso)) {
				ts = (long) (tso * MICRO_TO_NANO);
			}
			char phase = ((String) root.getOrDefault(ITraceEventConstants.PHASE, "I")).charAt(0); //$NON-NLS-1$
			String name = String
					.valueOf(root.getOrDefault(ITraceEventConstants.NAME, 'E' == phase ? "exit" : "unknown")); //$NON-NLS-1$ //$NON-NLS-2$
			Long tid = (Long) root.getOrDefault(ITraceEventConstants.TID, null);
			Object pid = root.get(ITraceEventConstants.PID);
			Double duration = (Double) root.getOrDefault(ITraceEventConstants.DURATION, Double.NaN);
			if (Double.isFinite(duration)) {
				duration = (duration * MICRO_TO_NANO);
			}
			String category = (String) root.get(ITraceEventConstants.CATEGORY);
			String id = (String) root.get(ITraceEventConstants.ID);
			Map args = (Map) root.getOrDefault(ITraceEventConstants.ARGS, null);
			if (args != null) {
				Set<String> keys = args.keySet();
				for (String key : keys) {
					Object value = args.get(key);
					argsMap.put("arg/" + key, String.valueOf(value));
				}
			}
			argsMap.put(ITraceEventConstants.TIMESTAMP, ts);
			argsMap.put(ITraceEventConstants.PHASE, phase);
			argsMap.put(ITraceEventConstants.NAME, name);
			if (tid != null) {
				argsMap.put(ITraceEventConstants.TID, tid);
			}
			if (pid != null) {
				argsMap.put(ITraceEventConstants.PID, pid);
			}
			if (Double.isFinite(duration)) {
				argsMap.put(ITraceEventConstants.DURATION, duration);
			}
			return true;
		} catch (ParseException e1) {
			// invalid, return null and it will fail
		}
		return false;
	}
}