package org.jocean.restfuldemo.ctrl;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.svr.annotation.HandleError;
import org.jocean.svr.annotation.OnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.unfbx.chatgpt.OpenAiClient;
import com.unfbx.chatgpt.entity.completions.CompletionResponse;

import io.netty.handler.codec.http.HttpRequest;


@Path("/newrest/")
@Controller
@Scope("prototype")
public class ChatgptDemo {
    private static final Logger LOG = LoggerFactory.getLogger(ChatgptDemo.class);

    @Value("${openai.apikey}")
    String _openaiApiKey;

    @HandleError(Exception.class)
    String handleAllError(final HttpRequest req, final Exception e) {
        LOG.warn("error when {}, detail: {}", req.uri(), e);
        return "error when " + req.uri() + "{\n" + ExceptionUtils.exception2detail(e) + "\n}";
    }

    @Path("chatgpt/ask")
    @OnError({
        "this.handleAllError"
        })
    public String ask(@QueryParam("q") final String question) {
    	LOG.info("chatgpt ask question {}", question);
    	
        final OpenAiClient openAiClient = new OpenAiClient(_openaiApiKey,60,60,60);
        CompletionResponse completions = openAiClient.completions(question);
        return Arrays.toString(completions.getChoices());
    }
}
