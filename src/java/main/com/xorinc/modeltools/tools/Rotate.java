package com.xorinc.modeltools.tools;

import static com.xorinc.modeltools.Main.verbose;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;

import org.apache.commons.lang3.Validate;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.xorinc.modeltools.Main;
import com.xorinc.modeltools.Main.ToolException;


public class Rotate implements Tool<Rotate> {

	public static final Rotate inst = new Rotate();
	public static final JsonParser jparser = new JsonParser();
		
	private static enum Axis { X, Y, Z }
	
	@Override
	public void execute(InputStream in, OutputStream out, Tool.Args<Rotate> args) throws ToolException {

		Args a = (Args) args;
		
		try(Reader r = new InputStreamReader(in); Writer w = new OutputStreamWriter(out)) {
						
			JsonObject tree = new JsonParser().parse(r).getAsJsonObject();
			
			verbose(tree);
			
			Validate.isTrue(a.angle % 90 == 0, "angle must me a multiple of 90");
			
			Axis axis = Axis.valueOf(a.axis.toUpperCase());
			
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
				
				DoubleBinaryOperator[][] ops = {minOrMax(fx, tx), minOrMax(fy, ty), minOrMax(fz, tz)};
				
				if(element.get("faces") != null){
					JsonObject faces = element.get("faces").getAsJsonObject();
					
					String[] names;

					switch(axis) {
					
					default:
					case X: names = "up,north,down,south".split(","); break;
					case Y: names = "up,east,down,west".split(","); break;
					case Z: names = "east,north,west,south".split(","); break;
						
					}
					
					List<String> nList = Arrays.asList(names);
					
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
								uv.add(new JsonPrimitive(16 - ty));
								uv.add(new JsonPrimitive(tx));
								uv.add(new JsonPrimitive(16 - fy));
								
							} break;
														
							case "east": case "west": {
								
								uv.add(new JsonPrimitive(fz));
								uv.add(new JsonPrimitive(16 - ty));
								uv.add(new JsonPrimitive(tz));
								uv.add(new JsonPrimitive(16 - fy));
								
							} break;
							
						}
						
						face.add("uv", uv);
						
						verbose(faceE.getKey() + " uv: " + uv);
						
						cullface:
						if(face.get("cullface") != null){
							
							String cull = face.get("cullface").getAsString();
							int index = nList.indexOf(cull);
							if(index == -1)
								break cullface;
							
							cull = nList.get((index + a.angle / 90) % nList.size());
							face.addProperty("cullface", cull);
						}
					}
										
					 List<JsonElement> els = Arrays.stream(names)
							 					.map(s -> faces.get(s))
							 					.collect(Collectors.toList());
					 
					 IntStream.range(0, els.size())
					 			.forEach(i -> faces.add(names[i], els.get((i + a.angle / 90) % els.size())));
					 
					 verbose(faces);
					 
					 faces.entrySet().stream()
					 			.filter(x -> !nList.contains(x.getKey()))
					 			.map(x -> x.getValue())
					 			.forEach(x -> {
					 				
					 				JsonPrimitive rotation = x.getAsJsonObject().getAsJsonPrimitive("rotation");
					 				if(rotation == null)
					 					rotation = new JsonPrimitive(0);
					 				
					 				rotation = new JsonPrimitive((rotation.getAsInt() + a.angle) % 360);
					 				x.getAsJsonObject().add("rotation", rotation);
					 				
					 			});
				}
				
				RealVector origin = new ArrayRealVector(new double[] {a.originX, a.originY, a.originZ});
								
				RealVector newFrom = rotate(new ArrayRealVector(new double[] {fx, fy, fz}).subtract(origin), a.angle, axis).add(origin);
				
				RealVector newTo = rotate(new ArrayRealVector(new double[] {tx, ty, tz}).subtract(origin), a.angle, axis).add(origin);
				
				JsonArray from2 = new JsonArray();
				JsonArray to2 = new JsonArray();
				
				IntStream.range(0, 3).forEach(i -> {
					
					from2.add(new JsonPrimitive(ops[i][0].applyAsDouble(newFrom.getEntry(i), newTo.getEntry(i))));
					to2.add(new JsonPrimitive(ops[i][1].applyAsDouble(newFrom.getEntry(i), newTo.getEntry(i))));
					
				});
				
				element.add("from", from2);
				element.add("to", to2);
				
				verbose("new from:" + from2);
				verbose("new to:" + to2);
				
				if(element.get("rotation") != null){
					
					JsonObject rotation = element.getAsJsonObject("rotation");
					JsonArray or = rotation.getAsJsonArray("origin");
					double ox = or.get(0).getAsDouble();
					double oy = or.get(1).getAsDouble();
					double oz = or.get(2).getAsDouble();
					
					verbose("origin:" + or);
					
					RealVector newOr = rotate(new ArrayRealVector(new double[] {ox, oy, oz}).subtract(origin), a.angle, axis).add(origin);
					
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

	public static class Args implements Tool.Args<Rotate> {
		
		int angle;
		double originX, originY, originZ;
		String axis;
		
		Args(int angle, double x, double y, double z, String axis){
			this.angle = angle;
			this.originX = x;
			this.originY = y;
			this.originZ = z;
			this.axis = axis;
		}
		
		public String toString() {
			
			return String.format("angle: %d, origin: [ %f, %f, %f ], axis: %s", angle, originX, originY, originZ, axis);
		}
	}
	
	private final ValueConverter<Args> parser = new ValueConverter<Args>() {
		
		private final String valuePattern = "<angle>,<axis>[,x,y,z]";
		private final double[] defaults = {8, 8, 8};
		
		@Override
		public Args convert(String s) {

			try{
				
				String[] parts = s.split(",");
				
				int angle = Integer.parseInt(parts[0]);
				String axis = parts[1];
				
				if(parts.length >= 5){
					return new Args(angle, Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Double.parseDouble(parts[4]), axis);
				}
				else {
					return new Args(angle, defaults[0], defaults[1], defaults[2], axis);
				}
				
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
	
	public RealVector rotate(RealVector v, double angle, Axis axis) {
		
		double[] points;
		
		switch(axis) {
		
		default:
		case X: points = new double[] { v.getEntry(1), v.getEntry(2) }; break;
		case Y: points = new double[] { v.getEntry(0), v.getEntry(2) }; break;
		case Z: points = new double[] { v.getEntry(0), v.getEntry(1) }; break;
		}
		
		AffineTransform.getRotateInstance(Math.toRadians(angle), 0, 0)
			.transform(points, 0, points, 0, 1);
		
		switch(axis) {
		
		default:
		case X: 
			v.setEntry(1, points[0]); 
			v.setEntry(2, points[1]);
			break;
		case Y: 
			v.setEntry(0, points[0]); 
			v.setEntry(2, points[1]);
			break;
		case Z: 
			v.setEntry(0, points[0]); 
			v.setEntry(1, points[1]);
			break;
		}
		
		return v;
	}
	
	public DoubleBinaryOperator[] minOrMax(double a, double b){
		
		if(a > b){
			return new DoubleBinaryOperator[] {Math::max, Math::min};
		}
		else
			return new DoubleBinaryOperator[] {Math::min, Math::max};
		
	}
	
}
