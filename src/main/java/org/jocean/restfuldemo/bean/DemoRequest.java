/**
 * 
 */
package org.jocean.restfuldemo.bean;

import javax.ws.rs.QueryParam;

import com.alibaba.fastjson.annotation.JSONField;


/**
 * @author isdom
 *
 */
public class DemoRequest {
    
    @JSONField(name="name")
    public String getName() {
        return _name;
    }

    @JSONField(name="name")
    public void setName(final String name) {
        this._name = name;
    }

    @Override
    public String toString() {
        return "DemoRequest [name=" + _name + "]";
    }

    @QueryParam("name")
    private String _name;
}
