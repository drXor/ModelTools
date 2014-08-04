package com.xorinc.modeltools.tools;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import joptsimple.ValueConverter;


public interface Tool<T extends Tool<T>> {

	void work(InputStream in, OutputStream out, Args<T> args) throws Exception;
	
	ValueConverter<? extends Args<T>> getParser();
	
	public interface Args<T extends Tool<T>> {}
	
}
