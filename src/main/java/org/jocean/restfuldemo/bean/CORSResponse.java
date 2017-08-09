package org.jocean.restfuldemo.bean;

import javax.ws.rs.HeaderParam;

public class CORSResponse {
    
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[Access-Control-Allow-Origin=")
                .append(_accessControlAllowOrigin).append(", errcode=")
//                .append(_errcode).append(", errmsg=").append(_errmsg)
                .append("]");
        return builder.toString();
    }

//    protected CORSResponse() {
//        this._errcode = 0;
//        this._errmsg = "ok";
//    }
//    
//    public CORSResponse(final int errcode, final String errmsg) {
//        this._errcode = errcode;
//        this._errmsg = errmsg;
//    }
//    
//    @JSONField(name="errcode")
//    public int getErrcode() {
//        return _errcode;
//    }
//    
//    @JSONField(name="errcode")
//    public void setErrcode(int errcode) {
//        this._errcode = errcode;
//    }
//    
//    @JSONField(name="errmsg")
//    public String getErrmsg() {
//        return _errmsg;
//    }
//    
//    @JSONField(name="errmsg")
//    public void setErrmsg(String errmsg) {
//        this._errmsg = errmsg;
//    }

    public void setAccessControlAllowOrigin(final String accessControlAllowOrigin) {
        this._accessControlAllowOrigin = accessControlAllowOrigin;
    }
    
    @HeaderParam("Access-Control-Allow-Origin")
    protected String _accessControlAllowOrigin;
    
//    protected int _errcode;
//    protected String _errmsg;
}
