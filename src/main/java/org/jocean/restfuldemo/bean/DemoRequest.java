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

    @JSONField(name="sex")
    public String getSex() {
        return _sex;
    }

    @JSONField(name="sex")
    public void setSex(final String sex) {
        this._sex = sex;
    }
    

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("DemoRequest [name=").append(_name).append(", sex=").append(_sex).append("]");
        return builder.toString();
    }


    @QueryParam("name")
    private String _name;
    
    @QueryParam("sex")
    private String _sex;
}
