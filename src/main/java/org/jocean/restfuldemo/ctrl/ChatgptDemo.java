package org.jocean.restfuldemo.ctrl;

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
import com.unfbx.chatgpt.entity.common.Choice;
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
        final CompletionResponse completions = openAiClient.completions(question);
        final Choice[] choices = completions.getChoices();
        if (choices.length >= 1) {
            return choices[0].getText();
        } else {
            return "(null)";
        }
    }
}
