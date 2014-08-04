package com.xorinc.modeltools.tools;

import static com.xorinc.modeltools.Main.verbose;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import com.google.gson.*;
import com.xorinc.modeltools.Util;

import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;


public class Resize implements Tool<Resize> {

	public static final Resize inst = new Resize();
	
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
	@Override
	public void work(InputStream in, OutputStream out, Tool.Args<Resize> args) throws Exception {

		Args a = (Args) args;
		
		try(Reader r = new InputStreamReader(in); Writer w = new OutputStreamWriter(out)) {
			
			JsonElement tree = new JsonParser().parse(r);
			
			verbose(tree);
			
			JsonArray elements = tree.getAsJsonObject().getAsJsonArray("elements");
			
			for(JsonElement el : elements){
				
				JsonObject element = el.getAsJsonObject();
				
				JsonArray from = element.getAsJsonArray("from");
				JsonArray to = element.getAsJsonArray("to");
				
				verbose("from:" + from);
				verbose("to:" + to);
				
				double fx = from.get(0).getAsDouble();
				double fy = from.get(1).getAsDouble();
				double fz = from.get(2).getAsDouble();
				
				double tx = to.get(0).getAsDouble();
				double ty = to.get(1).getAsDouble();
				double tz = to.get(2).getAsDouble();
				
				if(element.get("faces") != null){
					for(Entry<String, JsonElement> faceE : element.get("faces").getAsJsonObject().entrySet()){
						
						if(faceE.getValue().getAsJsonObject().get("uv") != null)
							continue;
						
						JsonObject face = faceE.getValue().getAsJsonObject();
						JsonArray uv = new JsonArray();
						
						switch(faceE.getKey()) {
						
							case "up": case "down": {
								
								uv.add(new JsonPrimitive(fx));
								uv.add(new JsonPrimitive(fz));
								uv.add(new JsonPrimitive(tx));
								uv.add(new JsonPrimitive(tz));
								
							} break;
						
							case "north": case "south": {
								
								uv.add(new JsonPrimitive(fx));
								uv.add(new JsonPrimitive(fy));
								uv.add(new JsonPrimitive(tx));
								uv.add(new JsonPrimitive(ty));
								
							} break;
														
							case "east": case "west": {
								
								uv.add(new JsonPrimitive(fz));
								uv.add(new JsonPrimitive(fy));
								uv.add(new JsonPrimitive(tz));
								uv.add(new JsonPrimitive(ty));
								
							} break;
							
						}
						
						face.add("uv", uv);
						
						verbose(faceE.getKey() + " uv: " + uv);
					}
				}
				
				RealVector origin = new ArrayRealVector(new double[] {a.originX, a.originY, a.originZ});
				
				RealVector newFrom = new ArrayRealVector(new double[] {fx, fy, fz}).subtract(origin).mapMultiplyToSelf(a.magnitude).add(origin);
				RealVector newTo = new ArrayRealVector(new double[] {tx, ty, tz}).subtract(origin).mapMultiplyToSelf(a.magnitude).add(origin);
				
				from = new JsonArray();
				from.add(new JsonPrimitive(newFrom.getEntry(0)));
				from.add(new JsonPrimitive(newFrom.getEntry(1)));
				from.add(new JsonPrimitive(newFrom.getEntry(2)));
				
				to = new JsonArray();
				to.add(new JsonPrimitive(newTo.getEntry(0)));
				to.add(new JsonPrimitive(newTo.getEntry(1)));
				to.add(new JsonPrimitive(newTo.getEntry(2)));
				
				element.add("from", from);
				element.add("to", to);
				
				verbose("new from:" + from);
				verbose("new to:" + to);
				
				if(element.get("rotation") != null){
					
					JsonObject rotation = element.getAsJsonObject("rotation");
					JsonArray or = rotation.getAsJsonArray("origin");
					double ox = or.get(0).getAsDouble();
					double oy = or.get(1).getAsDouble();
					double oz = or.get(2).getAsDouble();
					
					verbose("origin:" + or);
					
					RealVector newOr = new ArrayRealVector(new double[] {ox, oy, oz}).subtract(origin).mapMultiplyToSelf(a.magnitude).add(origin);
					
					or = new JsonArray();
					or.add(new JsonPrimitive(newOr.getEntry(0)));
					or.add(new JsonPrimitive(newOr.getEntry(1)));
					or.add(new JsonPrimitive(newOr.getEntry(2)));
					
					verbose("new origin:" + or);
					
					rotation.add("origin", or);
				}
			}
			
			String result = gson.toJson(tree);
			
			result = Util.formatJSON(result);
							
			w.append(result);
		}
	}

	@Override
	public ValueConverter<Args> getParser() {

		return parser;
	}

	public static class Args implements Tool.Args<Resize> {
		
		public final double magnitude, originX, originY, originZ;
		
		Args(double magnitude, double x, double y, double z){
			this.magnitude = magnitude;
			this.originX = x;
			this.originY = y;
			this.originZ = z;
		}
		
		public String toString() {
			
			return String.format("magnitude: %f, origin: [ %f, %f, %f ]", magnitude, originX, originY, originZ);
		}
	}
	
	private final ValueConverter<Args> parser = new ValueConverter<Args>() {

		private final int[] defaults = {8, 8, 8};
		
		private final String valuePattern = "<magnitude>[;<originx>,<originy>,<originz>]";
		
		private final String realPattern = "[0-9]+\\.?[0-9]*|[0-9]*\\.[0-9]+";
		
		private final Pattern pattern = Pattern.compile("(" + realPattern + ")(?:;(" + realPattern + "),(" + realPattern + "),(" + realPattern + "))?");
		
		@Override
		public Args convert(String s) {

			Matcher m = pattern.matcher(s);
			
			if(!m.matches()){
				throw new ValueConversionException("argument does not match expected pattern " + valuePattern);
			}
			try {
				
				double mag = Double.parseDouble(m.group(1));
				double x = m.group(2) != null ? Double.parseDouble(m.group(2)) : defaults[0];
				double y = m.group(3) != null ? Double.parseDouble(m.group(3)) : defaults[1];
				double z = m.group(4) != null ? Double.parseDouble(m.group(4)) : defaults[2];
				
				return new Args(mag, x, y, z);
				
			} catch (Exception e) {
				throw new ValueConversionException("error while parsing --resize options", e);
			}
		}

		@Override
		public String valuePattern() {

			return valuePattern;
		}

		@Override
		public Class<Args> valueType() {

			return Args.class;
		}
		
	};
	
}
