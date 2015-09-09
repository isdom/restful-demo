/**
 * 
 */
package org.jocean.restfuldemo.bean.outbound;

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import com.alibaba.fastjson.annotation.JSONField;


/**
 * @author isdom
 *
 */
@Path("/boards")
public class OutboundRequest {
    
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
        return "OutboundRequest [name=" + _name + "]";
    }

    @QueryParam("name")
    private String _name;
}
