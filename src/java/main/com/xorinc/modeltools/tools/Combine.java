package com.xorinc.modeltools.tools;

import static com.xorinc.modeltools.Main.verbose;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.gson.*;
import com.xorinc.modeltools.Main;
import com.xorinc.modeltools.Main.ToolException;
import com.xorinc.modeltools.Util;

import joptsimple.ValueConverter;


public class Combine implements Tool<Combine> {

	public static final Combine inst = new Combine();
	public static final JsonParser jparser = new JsonParser();	
	
	private final Supplier<JsonObject> objCtr = JsonObject::new;
	private final Supplier<JsonArray> arrCtr = JsonArray::new;
	
	@Override
	public void execute(InputStream in, OutputStream out, Tool.Args<Combine> args) throws ToolException {

		Args a = (Args) args;
		
		try(Reader r = new InputStreamReader(in); ) {
						
			JsonObject tree = new JsonParser().parse(r).getAsJsonObject();
			
			verbose(tree);
			
			JsonObject textures = Util.getOrElse(tree.getAsJsonObject("textures"), objCtr);
			JsonArray elements =  Util.getOrElse(tree.getAsJsonArray("elements"), arrCtr);
			
			Set<List<Entry<String, JsonObject>>> product = Sets.cartesianProduct(a.elements.stream().map(Map::entrySet).collect(Collectors.toList()));
			
			verbose(product);
			
			product.stream().forEach(l -> {
				
				StringBuilder name = new StringBuilder(FileUtils.removeExtension(Main.currentOut.getName()));
				JsonObject newTex = new JsonObject();
				JsonArray newEl = new JsonArray();
				textures.entrySet().forEach(e -> newTex.add(e.getKey(), e.getValue()));
				newEl.addAll(elements);
				
				l.forEach(e -> {
					
					JsonObject tree_ = Util.getOrElse(e.getValue(), objCtr);
					
					JsonObject textures_ = Util.getOrElse(tree_.getAsJsonObject("textures"), objCtr);
					JsonArray elements_ =  Util.getOrElse(tree_.getAsJsonArray("elements"), arrCtr);
					
					name.append("_" + e.getKey());
					textures_.entrySet().forEach(e1 -> newTex.add(e1.getKey(), e1.getValue()));
					newEl.addAll(elements_);
				});
				
				tree.add("elements", newEl);
				tree.add("textures", newTex);
				
				verbose(name);
				verbose(newTex);
				
				try(Writer w = new FileWriter(new File(Main.currentOut.getParentFile(), name + ".json"))){
					
					Main.writeFormatted(tree, w);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			
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

	public static class Args implements Tool.Args<Combine> {
		
		public final List<Map<String, JsonObject>> elements;
		
		Args(List<Map<String, JsonObject>> elements){
			this.elements = elements;
		}
		
		public String toString() {
			
			return elements.toString();
		}
	}
	
	private final ValueConverter<Args> parser = new ValueConverter<Args>() {
		
		private final String valuePattern = "<file>,[file,[...]] [<file>,[file,[...]] [...]]";
		
		@Override
		public Args convert(String s) {

			ImmutableList.Builder<Map<String, JsonObject>> list = ImmutableList.builder();
			
			for(String s2 : s.split("(?<!////) ")){
				
				ImmutableMap.Builder<String, JsonObject> map = ImmutableMap.builder();
				
				for(String s3 : s2.split("(?<!////),")){
					File f = new File(s3);
					try(Reader r = new FileReader(f)){
						map.put(FileUtils.removeExtension(f.getName()), jparser.parse(r).getAsJsonObject());
					}
					catch (Exception e) {
						map.put(FileUtils.removeExtension(f.getName()), null);
					}
					
				}
				
				list.add(map.build());
			}
			
			return new Args(list.build());
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

	@Override
	public boolean multifile() {

		return false;
	}
	
}
