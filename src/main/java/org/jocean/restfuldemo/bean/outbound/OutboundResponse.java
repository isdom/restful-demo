package org.jocean.restfuldemo.bean.outbound;

import java.util.Arrays;

import com.alibaba.fastjson.annotation.JSONField;


public class OutboundResponse {
        
    @JSONField(name="boards")
    public String[] getBoards() {
        return _boards;
    }

    @JSONField(name="boards")
    public void setBoards(final String[] boards) {
        this._boards = boards;
    }

    @Override
    public String toString() {
        return "OutboundResponse [_boards=" + Arrays.toString(_boards) + "]";
    }

    private String[] _boards;
}