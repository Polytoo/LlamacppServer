package org.mark.llamacpp.server.struct;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型路径配置结构
 */
public class ModelPathConfig {

    private List<ModelPathDataStruct> items = new ArrayList<>();

    public ModelPathConfig() {

    }

    public List<ModelPathDataStruct> getItems() {
        return this.items;
    }

    public void setItems(List<ModelPathDataStruct> items) {
        this.items = items;
    }
}
