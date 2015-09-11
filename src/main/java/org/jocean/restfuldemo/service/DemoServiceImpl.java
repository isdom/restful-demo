package org.jocean.restfuldemo.service;

public class DemoServiceImpl implements DemoService {

    @Override
    public String getAction() {
        return this._action;
    }

    public void setAction(final String action) {
        this._action = action;
    }
    
    private String _action;
}
