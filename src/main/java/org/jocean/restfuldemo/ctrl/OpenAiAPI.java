package org.jocean.restfuldemo.ctrl;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

import org.jocean.rpc.annotation.RpcBuilder;

import com.alibaba.fastjson.annotation.JSONField;

import rx.Observable;

// ref: https://platform.openai.com/docs/guides/chat
//      https://platform.openai.com/docs/api-reference/chat

public interface OpenAiAPI {

    interface ChatChoice {
        @JSONField(name = "index")
        public int getIndex();

        @JSONField(name = "index")
        public void setIndex(final int idx);

        // text：
        @JSONField(name = "text")
        public String getText();

        // text：
        @JSONField(name = "text")
        public void setText(final String text);

        // finish_reason：
        @JSONField(name = "finish_reason")
        public String getFinish_reason();

        // finish_reason：
        @JSONField(name = "finish_reason")
        public void setFinish_reason(final String finish_reason);
    }

    interface ChatUsage {
        @JSONField(name = "prompt_tokens")
        public int getPrompt_tokens();

        @JSONField(name = "prompt_tokens")
        public void setPrompt_tokens(final int prompt_tokens);

        @JSONField(name = "completion_tokens")
        public int getCompletion_tokens();

        @JSONField(name = "completion_tokens")
        public void setCompletion_tokens(final int completion_tokens);

        @JSONField(name = "total_tokens")
        public int getTotal_tokens();

        @JSONField(name = "total_tokens")
        public void setTotal_tokens(final int total_tokens);
    }

    interface ChatCompletionResponse {
        @JSONField(name = "id")
        public String getId();

        @JSONField(name = "id")
        public void setId(final String id);

        // object：
        @JSONField(name = "object")
        public String getObject();

        // object：
        @JSONField(name = "object")
        public void setObject(final String object);

        // created：
        @JSONField(name = "created")
        public long getCreated();

        // created：
        @JSONField(name = "created")
        public void setCreated(final long created);

        // model：
        @JSONField(name = "model")
        public String getModel();

        // model：
        @JSONField(name = "model")
        public void setModel(final String model);

        // choices：
        @JSONField(name = "choices")
        public ChatChoice[] getChoices();

        // choices：
        @JSONField(name = "choices")
        public void setChoices(final ChatChoice[] choices);

        // usage：
        @JSONField(name = "usage")
        public ChatUsage getUsage();

        // usage：
        @JSONField(name = "usage")
        public void setUsage(final ChatUsage usage);
    }

    public class ChatMessage {
        private String _role;
        private String _content;

        ChatMessage(final String role, final String content) {
            this._role = role;
            this._content = content;
        }

        @JSONField(name="role")
        public String getRole() {
            return _role;
        }

        @JSONField(name="role")
        public void setRole(final String role) {
            this._role = role;
        }

        @JSONField(name="content")
        public String getContent() {
            return _content;
        }

        @JSONField(name="content")
        public void setContent(final String content) {
            this._content = content;
        }

    }

    // https://platform.openai.com/docs/api-reference/chat
    @RpcBuilder
    interface ChatCompletionBuilder {
        @HeaderParam("Authorization")
        public ChatCompletionBuilder authorization(final String authorization);

        //  model        string        Required
        //  ID of the model to use. Currently, only gpt-3.5-turbo and gpt-3.5-turbo-0301 are supported.
        @JSONField(name="model")
        public ChatCompletionBuilder model(final String model);

        //  model        string        Required
        //  ID of the model to use. Currently, only gpt-3.5-turbo and gpt-3.5-turbo-0301 are supported.
        @JSONField(name="messages")
        public ChatCompletionBuilder messages(final ChatMessage[] messages);

        //  temperature        number        Optional
        // Defaults to 1
        // What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic.
        // We generally recommend altering this or top_p but not both.
        @JSONField(name="temperature")
        public ChatCompletionBuilder temperature(final Float temperature);

        //  top_p        number        Optional
        // Defaults to 1
        // An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10% probability mass are considered.
        // We generally recommend altering this or temperature but not both.
        @JSONField(name="top_p")
        public ChatCompletionBuilder top_p(final Float top_p);

        //  n        integer        Optional
        // Defaults to 1
        // How many chat completion choices to generate for each input message.
        @JSONField(name="n")
        public ChatCompletionBuilder n(final Integer n);

        //  max_tokens        integer        Optional
        //  Defaults to inf
        //  The maximum number of tokens allowed for the generated answer. By default, the number of tokens the model can return will be (4096 - prompt tokens).
        @JSONField(name="max_tokens")
        public ChatCompletionBuilder max_tokens(final Integer max_tokens);

        //  presence_penalty        number        Optional
        //  Defaults to 0
        //  Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they appear in the text so far, increasing the model's likelihood to talk about new topics.
        //  See more information about frequency and presence penalties.
        @JSONField(name="presence_penalty")
        public ChatCompletionBuilder presence_penalty(final Float presence_penalty);

        //  frequency_penalty        number        Optional
        //  Defaults to 0
        //  Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
        //  See more information about frequency and presence penalties.
        @JSONField(name="frequency_penalty")
        public ChatCompletionBuilder frequency_penalty(final Float frequency_penalty);

        //  user        string        Optional
        //  A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse. Learn more.
        @JSONField(name="user")
        public ChatCompletionBuilder user(final String user);

        @POST
        @Path("https://api.openai.com/v1/chat/completions")
        @Consumes(MediaType.APPLICATION_JSON)
        Observable<ChatCompletionResponse> call();
    }

    public ChatCompletionBuilder chatCompletion();
}
