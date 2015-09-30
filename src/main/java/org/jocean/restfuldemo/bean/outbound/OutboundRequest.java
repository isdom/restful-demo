/**
 * 
 */
package org.jocean.restfuldemo.bean.outbound;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.jocean.idiom.AnnotationWrapper;

import com.alibaba.fastjson.annotation.JSONField;


/**
 * @author isdom
 *
 */
@Path("/boards/{boardid}")
//@AnnotationWrapper(POST.class)  // 用 POST HTTP Method 请求
public class OutboundRequest {
    
    @JSONField(name="name")
    public String getName() {
        return _name;
    }

    @JSONField(name="name")
    public void setName(final String name) {
        this._name = name;
    }

    public void setBoardId(final String id) {
        this._boardid = id;
    }
    
    @Override
    public String toString() {
        return "OutboundRequest [name=" + _name + "]";
    }

    @QueryParam("name")
    private String _name;
    
    @PathParam("boardid")
    private String _boardid = "1998";
}
