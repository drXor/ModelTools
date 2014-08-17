package com.xorinc.modeltools.tools;

import static com.xorinc.modeltools.Main.verbose;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import com.google.common.collect.ImmutableList;
import com.google.gson.*;
import com.xorinc.modeltools.Main;
import com.xorinc.modeltools.Main.ToolException;
import com.xorinc.modeltools.Util;

import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;


public class ResizeItem implements Tool<ResizeItem> {

	public static final ResizeItem inst = new ResizeItem();
		
	@Override
	public void execute(InputStream in, OutputStream out, Tool.Args<ResizeItem> args) throws ToolException {

		Args a = (Args) args;
		
		try(Reader r = new InputStreamReader(in); Writer w = new OutputStreamWriter(out)) {
			
			JsonElement tree = new JsonParser().parse(r);
			
			verbose(tree);
			
			JsonObject display = tree.getAsJsonObject().getAsJsonObject("display");
			
			for(Entry<String, JsonElement> el : display.entrySet()){
				
				if(!a.items.isEmpty() && !a.items.contains(el.getKey().toLowerCase()))
					continue;
				
				JsonObject element = el.getValue().getAsJsonObject();
				
				JsonArray scale = element.getAsJsonArray("scale");
				
				verbose("scale:" + scale);
				
				double sx = scale.get(0).getAsDouble();
				double sy = scale.get(1).getAsDouble();
				double sz = scale.get(2).getAsDouble();
				
				RealVector origin = new ArrayRealVector(new double[] {a.originX, a.originY, a.originZ});
				
				RealVector newScale = new ArrayRealVector(new double[] {sx, sy, sz}).subtract(origin).mapMultiplyToSelf(a.magnitude).add(origin);
				
				scale = new JsonArray();
				scale.add(new JsonPrimitive(newScale.getEntry(0)));
				scale.add(new JsonPrimitive(newScale.getEntry(1)));
				scale.add(new JsonPrimitive(newScale.getEntry(2)));
				
				element.add("scale", scale);
				
				verbose("new scale:" + scale);
			}
			
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

	public static class Args implements Tool.Args<ResizeItem> {
		
		public final double magnitude, originX, originY, originZ;
		public final List<String> items;
		
		Args(double magnitude, double x, double y, double z, List<String> items){
			this.magnitude = magnitude;
			this.originX = x;
			this.originY = y;
			this.originZ = z;
			this.items = ImmutableList.copyOf(items);
		}
		
		public String toString() {
			
			return String.format("magnitude: %f, origin: [ %f, %f, %f ], ", magnitude, originX, originY, originZ);
		}
	}
	
	private final ValueConverter<Args> parser = new ValueConverter<Args>() {

		private final int[] defaults = {0, 0, 0};
		
		private final String valuePattern = "<magnitude>[;<originx>,<originy>,<originz>][;<item>[,<item>[...]]]";
		
		private final String realPattern = "[0-9]+\\.?[0-9]*|[0-9]*\\.[0-9]+";
		
		private final Pattern pattern = Pattern.compile("(" + realPattern + ")(?:;(" + realPattern + "),(" + realPattern + "),(" + realPattern + "))?(?:;([a-zA-Z]+(?:,[a-zA-Z]+)*))?");
		
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
				
				String[] items = m.group(5) != null ? m.group(5).split(",") : new String[] {};
				
				for(int i = 0; i < items.length; i++){
					items[i] = items[i].toLowerCase();
				}
				
				return new Args(mag, x, y, z, Arrays.asList(items));
				
			} catch (Exception e) {
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
