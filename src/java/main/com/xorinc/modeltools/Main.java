package com.xorinc.modeltools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtil;
import org.apache.commons.lang3.ArrayUtils;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.xorinc.modeltools.tools.Combine;
import com.xorinc.modeltools.tools.Resize;
import com.xorinc.modeltools.tools.ResizeItem;
import com.xorinc.modeltools.tools.Rotate;
import com.xorinc.modeltools.tools.Tool;
import com.xorinc.modeltools.tools.Translate;
import com.xorinc.modeltools.tools.Tool.Args;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConversionException;


public class Main {

	private static boolean verbose;
	
	private static Gson gson;
	private static String indent;
	
	public static File currentIn, currentOut;
	
	public static void main(String... args) throws Throwable{
		
		if(System.in.available() > 0) {
			List<String> pipe = new BufferedReader(new InputStreamReader(System.in)).lines().collect(Collectors.toList());
			
			args = ArrayUtils.addAll(args, pipe.toArray(new String[pipe.size()]));
		}
		
		OptionParser parser = new OptionParser();
		OptionSpec<Void> help = parser.acceptsAll(Arrays.asList("?", "help"), "Prints this message.").forHelp();
		OptionSpec<Void> verboseOpt = parser.acceptsAll(Arrays.asList("v", "verbose"), "Verbose output.");
		OptionSpec<String> suffix = parser.acceptsAll(Arrays.asList("s", "suffix"), "Suffix for output.").withRequiredArg();
		OptionSpec<File> output = parser.acceptsAll(Arrays.asList("o", "out", "output"), "Output file.").withRequiredArg().ofType(File.class);
		// TODO OptionSpec<?> recurse = parser.acceptsAll(Arrays.asList("r", "recursive"), "Applies the tool to all directories recursively.");
		OptionSpec<String> toolArg = parser.acceptsAll(Arrays.asList("t", "tool"), "The tool to use (required).").withRequiredArg();
		OptionSpec<String> toolOpt = parser.acceptsAll(Arrays.asList("opt", "toolOptions"), "Options for a tool.").withRequiredArg();
		OptionSpec<String> indentOpt = parser.acceptsAll(Arrays.asList("i", "indent", "indentation"), "Indentation string.").withRequiredArg().defaultsTo("    ");
		OptionSpec<File> fileArg = parser.nonOptions("file").ofType(File.class);
		
		OptionSet options = null;
		
		try{
			options = parser.parse(args);
		} catch (OptionException ignore) {}
		
		if(options == null || options.has(help) || !options.has(toolArg)){
			
			try {
				parser.printHelpOn(System.out);
			}
			catch (IOException e) {
				System.err.println("Error printing help!");
				e.printStackTrace();
			}
			return;
		}
		
		verbose = options.has(verboseOpt);
		gson = new GsonBuilder().setPrettyPrinting().create();
		indent = indentOpt.value(options);
		
		
		Tool<? extends Tool<?>> t;
		Args<? extends Tool<?>> a = null;
		
		switch(toolArg.value(options).toLowerCase()) {
		
		case "resize": t = Resize.inst; break;
		case "resizeitem": t = ResizeItem.inst; break;
		case "combine": t = Combine.inst; break;
		case "rotate": t = Rotate.inst; break;
		case "translate": t = Translate.inst; break;
		
		default: 
			System.err.printf("Tool %s does not exist!", toolArg.value(options).toLowerCase());
			return;
		}
		
		if(t.getParser() != null && !options.has(toolOpt))
			throw new ValueConversionException("Tool requires option: " + t.getParser().valuePattern());
		
		try {
			if(t.getParser() != null)
				a = (Args<? extends Tool<?>>) t.getParser().convert(toolOpt.value(options));
			
		} catch (ValueConversionException e){
			
			System.err.println(e.getMessage());
			return;
		}
		
		for (File in : fileArg.values(options)){
			verbose(in);
			File out;
			File temp = File.createTempFile(".ModelToolstemp", "", in.getParentFile());
			
			if(options.has(suffix)){
				String su = suffix.value(options);
				String name = in.getName();
				String ext = FileUtils.getExtension(name);
				name = FileUtils.removeExtension(name);
				out = new File(in.getParent(), name + su + "." + ext);
			} else if(options.has(output)){
				out = output.value(options);
			} else {
				out = in;
			}
			
			currentIn = in;
			currentOut = out;
			
			try(InputStream is = new FileInputStream(in); OutputStream os = new FileOutputStream(temp)){
					
				t.execute(is, os, (Args) a);
				
				out.delete();
				out.createNewFile();
				FileUtils.copyFile(temp, out);
				
			} catch (ToolException e) {
				
				System.err.printf("Error executing on file `%s'!", in.getName());
				System.err.println(e.getMessage());
				
			} catch (Exception e) {
				
				System.err.println("Error applying tool!");
				e.printStackTrace();
			}			
		}
	}
	
	public static void verbose(Object o){
		
		if(verbose) System.out.println(o);
	}
	
	public static void write(JsonElement tree, Writer w){
		
		JsonWriter jw = new JsonWriter(w);
		jw.setIndent(indent);
		gson.toJson(tree, jw);
	}
	
	public static String toJson(JsonElement tree){
		
		StringWriter w = new StringWriter();
		write(tree, w);
		return w.toString();
	}
	
	public static void writeFormatted(JsonElement tree, Writer w) throws IOException{
		
		w.append(Util.formatJSON(toJson(tree)));
	}
	
	public static class ToolException extends Exception {

		private static final long serialVersionUID = 1L;

		public ToolException(String string) {

			super(string);
		}
	}
}
