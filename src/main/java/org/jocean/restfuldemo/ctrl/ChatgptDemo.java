package org.jocean.restfuldemo.ctrl;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.MessageBody;
import org.jocean.http.MessageUtil;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.restfuldemo.ctrl.OpenAiAPI.ChatChoice;
import org.jocean.restfuldemo.ctrl.OpenAiAPI.ChatMessage;
import org.jocean.svr.annotation.HandleError;
import org.jocean.svr.annotation.OnError;
import org.jocean.svr.annotation.RpcFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.unfbx.chatgpt.OpenAiClient;
import com.unfbx.chatgpt.entity.common.Choice;
import com.unfbx.chatgpt.entity.completions.CompletionResponse;

import io.netty.handler.codec.http.HttpRequest;
import rx.Observable;


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
        if (choices.length >= 1 && choices[0].getText() != null) {
        	final String answer = choices[0].getText();
        	LOG.info("chatgpt answer {}", answer);
            return answer;
        } else {
            return "(null)";
        }
    }

    @Path("chatgpt/postask")
    @POST
    @OnError({
        "this.handleAllError"
        })
    public Observable<String> postask(final Observable<MessageBody> omb) {
    	return omb.flatMap(body -> MessageUtil.<String>decodeContentAs(body.content(), (is, type) -> MessageUtil.parseContentAsString(is), String.class)).map(question -> {
        	LOG.info("chatgpt ask question {}", question);

            final OpenAiClient openAiClient = new OpenAiClient(_openaiApiKey,60,60,60);
            final CompletionResponse completions = openAiClient.completions(question);
            final Choice[] choices = completions.getChoices();
            if (choices.length >= 1 && choices[0].getText() != null) {
            	final String answer = choices[0].getText();
            	LOG.info("chatgpt answer {}", answer);
                return answer;
            } else {
                return "(null)";
            }
    	});
    }

    @RpcFacade
    OpenAiAPI openai;

    @Path("chatgpt/chat")
    @POST
    public Observable<String> chat(final Observable<MessageBody> omb) {
        return omb.flatMap(body -> MessageUtil.<String>decodeContentAs(body.content(), (is, type) -> MessageUtil.parseContentAsString(is), String.class))
                .doOnNext(question ->  LOG.info("chatgpt ask question {}", question))
                .flatMap(question -> openai.chatCompletion()
                        .authorization("Bearer "+_openaiApiKey)
                        .model("gpt-3.5-turbo")
                        .messages(new ChatMessage[]{new ChatMessage("user", question)})
                        .call())
                .map( completion -> {
                      final ChatChoice[] choices = completion.getChoices();
                      if (choices.length >= 1 && choices[0].getMessage() != null && choices[0].getMessage().getContent() != null) {
                          final String answer = "(" + choices[0].getMessage().getRole() + "):" + choices[0].getMessage().getContent()
                                  + "/" + choices[0].getIndex() + "/" + choices[0].getFinish_reason()
                                  + "/" + "{prompt_tokens:" + completion.getUsage().getPrompt_tokens()
                                  + "/completion_tokens:" + completion.getUsage().getCompletion_tokens()
                                  + "/total_tokens:" + completion.getUsage().getTotal_tokens() + "}"
                                  ;
                          LOG.info("chatgpt: {}", answer);
                          return answer;
                      } else {
                          return "(error)";
                      }
                });

    }
}
