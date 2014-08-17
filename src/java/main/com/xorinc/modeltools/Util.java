package com.xorinc.modeltools;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.google.gson.*;

public class Util {

	private static Pattern coordPattern = Pattern.compile("\"(from|to|rotation|scale|translation)\":\\s*\\[\\s*(\\S+)\\s*,\\s*(\\S+)\\s*,\\s*(\\S+)\\s*\\]");
	private static Pattern rotationPattern = Pattern.compile("\"rotation\":\\s*\\{");
	private static Pattern facePattern = Pattern.compile("\"(up|down|north|south|east|west)\":\\s*\\{");
		
	private static final JsonParser parser = new JsonParser();
	
	public static String formatJSON(String json){
				
		Main.verbose(json);
		
		json = coordPattern.matcher(json).replaceAll("\"$1\": [ $2, $3, $4 ]");
		
		Matcher rotations = rotationPattern.matcher(json);
		
		String original = json;
		
		while(rotations.find()) {
			
			String tail = original.substring(rotations.start());
			String object = tail.substring(0, findBalance(tail, '{', '}') + 1);
			
			Main.verbose(object);
			
			String replacement = "\"rotation\": { ";
			
			JsonObject obj = parser.parse("{" + object + "}").getAsJsonObject().getAsJsonObject("rotation");
			
			JsonArray origin = obj.getAsJsonArray("origin");
			
			replacement += String.format("\"origin\": [ %s, %s, %s ], ", origin.get(0), origin.get(1), origin.get(2));
			
			replacement += String.format("\"axis\": %s, ", obj.getAsJsonPrimitive("axis"));
			
			replacement += String.format("\"angle\": %s }", obj.getAsJsonPrimitive("angle"));
			
			json = json.replace(object, replacement);
			
			Main.verbose(replacement);
		}
		
		Matcher faces = facePattern.matcher(json);
		
		original = json;
		
		while(faces.find()) {
			
			String tail = original.substring(faces.start());
			String object = tail.substring(0, findBalance(tail, '{', '}') + 1);
			
			Main.verbose(object);
			
			String face = faces.group(1);
			String replacement = String.format("\"%s\":%s{ ", face, StringUtils.repeat(' ', 6 - face.length()));
			
			JsonObject obj = parser.parse("{" + object + "}").getAsJsonObject().getAsJsonObject(faces.group(1));
			
			if(obj.get("uv") != null) {
				JsonArray uv = obj.getAsJsonArray("uv");
			
				replacement += String.format("\"uv\": [ %s, %s, %s, %s ], ", uv.get(0), uv.get(1), uv.get(2), uv.get(3));
			}
			
			if(obj.get("texture") != null) {
				
				replacement += String.format("\"texture\": %s, ", obj.getAsJsonPrimitive("texture"));
			}
			
			if(obj.get("cullface") != null) {
							
				replacement += String.format("\"cullface\": %s, ", obj.getAsJsonPrimitive("cullface"));
			}
			
			if(obj.get("rotation") != null) {
				
				replacement += String.format("\"rotation\": %s, ", obj.getAsJsonPrimitive("rotation"));
			}
			
			if(obj.get("tintindex") != null) {
				
				replacement += String.format("\"tintindex\": %s, ", obj.getAsJsonPrimitive("tintindex"));
			}
			
			replacement = replacement.substring(0, replacement.length() - 2) + " }";
			
			json = json.replace(object, replacement);
			
			Main.verbose(replacement);
		}
		
		return json;
	}
	
	public static int findBalance(String s, char left, char right) {
		
		boolean started = false;
		int score = 0;
		int index = 0;
		
		while(!started || score > 0){
			
			index++;
			
			if(index >= s.length())
				return -1;
			
			char c = s.charAt(index);
			
			if(c == left){
				score++;
				started = true;
			} else if (c == right) {
				score--;
				started = true;
			}
		}
		
		return index;
	}
	
	public static <T> T getOrElse(T t, Supplier<T> alt){
		
		return t == null ? alt.get() : t;
	}
	
	public static <T> T getOrElse(T t, T alt){
		
		return getOrElse(t, () -> t);
	}
	
}
