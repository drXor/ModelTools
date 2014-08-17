package com.xorinc.modeltools.tools;

import static com.xorinc.modeltools.Main.verbose;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Map.Entry;

import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.xorinc.modeltools.Main;
import com.xorinc.modeltools.Main.ToolException;


public class Translate implements Tool<Translate> {

	public static final Translate inst = new Translate();
	public static final JsonParser jparser = new JsonParser();
		
	@Override
	public void execute(InputStream in, OutputStream out, Tool.Args<Translate> args) throws ToolException {

		Args a = (Args) args;
		
		try(Reader r = new InputStreamReader(in); Writer w = new OutputStreamWriter(out)) {
						
			JsonObject tree = new JsonParser().parse(r).getAsJsonObject();
			
			verbose(tree);
									
			tree.getAsJsonArray("elements").forEach(e -> {
				
				JsonObject element = e.getAsJsonObject();
				
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
					JsonObject faces = element.get("faces").getAsJsonObject();

					for(Entry<String, JsonElement> faceE : faces.entrySet()){
						
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
								
				RealVector newFrom = new ArrayRealVector(new double[] {fx, fy, fz}).add(origin);
				RealVector newTo = new ArrayRealVector(new double[] {tx, ty, tz}).add(origin);
				
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
					
					RealVector newOr = new ArrayRealVector(new double[] {ox, oy, oz}).add(origin);
					
					or = new JsonArray();
					or.add(new JsonPrimitive(newOr.getEntry(0)));
					or.add(new JsonPrimitive(newOr.getEntry(1)));
					or.add(new JsonPrimitive(newOr.getEntry(2)));
					
					verbose("new origin:" + or);
					
					rotation.add("origin", or);
				}
				
			});
			
			Main.writeFormatted(tree, w);
			
		} catch (NullPointerException | ClassCastException e) {
			
			throw new ToolException("Malformed model format!");
		} catch (IOException e) {
			
			throw new ToolException("IOException:" + e.getMessage());
		}
		
	}

	@Override
	public ValueConverter<Args> getParser() {

		return parser;
	}

	public static class Args implements Tool.Args<Translate> {
		
		double originX, originY, originZ;
		
		Args(double x, double y, double z){
			this.originX = x;
			this.originY = y;
			this.originZ = z;
		}
		
		public String toString() {
			
			return String.format("[ %f, %f, %f ]", originX, originY, originZ);
		}
	}
	
	private final ValueConverter<Args> parser = new ValueConverter<Args>() {
		
		private final String valuePattern = "x,y,z";
		
		@Override
		public Args convert(String s) {

			try{
				
				String[] parts = s.split(",");
				
				return new Args(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
				
				
			} catch(Exception e) {
				throw new ValueConversionException("error while parsing options", e);
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
