package org.mark.llamacpp.server.struct;



/**
 * 	模型文件夹的配置结构。
 */
public class ModelPathDataStruct {
	
	/**
	 * 	
	 */
	private String path;
	
	/**
	 * 	
	 */
	private String name;
	
	/**
	 * 	描述。
	 */
	private String description;
	
	
	public ModelPathDataStruct() {
		
	}


	public String getPath() {
		return path;
	}


	public void setPath(String path) {
		this.path = path;
	}


	public String getName() {
		return name;
	}


	public void setName(String name) {
		this.name = name;
	}


	public String getDescription() {
		return description;
	}


	public void setDescription(String description) {
		this.description = description;
	}
}
