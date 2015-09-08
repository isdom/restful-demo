package org.jocean.restfuldemo.bean;

import com.alibaba.fastjson.annotation.JSONField;


public class DemoResponse {
        
    @JSONField(name="message")
    public String getMessage() {
        return _message;
    }

    @JSONField(name="message")
    public void setMessage(final String message) {
        this._message = message;
    }

    private String _message;
}