package com.xorinc.modeltools.tools;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import com.xorinc.modeltools.Main.ToolException;

import joptsimple.ValueConverter;


public interface Tool<T extends Tool<T>> {

	void execute(InputStream in, OutputStream out, Args<T> args) throws ToolException;
	
	ValueConverter<? extends Args<T>> getParser();
	
	public interface Args<T extends Tool<T>> {}
	
	default boolean multifile(){
		
		return false;
	}
}
