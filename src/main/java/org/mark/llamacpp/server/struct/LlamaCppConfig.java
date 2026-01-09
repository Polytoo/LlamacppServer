package org.mark.llamacpp.server.struct;

import java.util.ArrayList;
import java.util.List;




public class LlamaCppConfig {
	
	
	private List<LlamaCppDataStruct> items = new ArrayList<>();
	
	
	public LlamaCppConfig() {
		
	}
	
	
	public List<LlamaCppDataStruct> getItems(){
		return this.items;
	}
	
	
	public void setItems(List<LlamaCppDataStruct> items) {
		this.items = items;
	}
}
