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
	/*
	    +-------------------------------------------------+
	    |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
	+--------+-------------------------------------------------+----------------+
	|00000000| 48 54 54 50 2f 31 2e 31 20 32 30 30 20 4f 4b 0d |HTTP/1.1 200 OK.|
	|00000010| 0a 44 61 74 65 3a 20 4d 6f 6e 2c 20 30 36 20 4d |.Date: Mon, 06 M|
	|00000020| 61 72 20 32 30 32 33 20 30 38 3a 32 39 3a 34 38 |ar 2023 08:29:48|
	|00000030| 20 47 4d 54 0d 0a 43 6f 6e 74 65 6e 74 2d 54 79 | GMT..Content-Ty|
	|00000040| 70 65 3a 20 61 70 70 6c 69 63 61 74 69 6f 6e 2f |pe: application/|
	|00000050| 6a 73 6f 6e 0d 0a 43 6f 6e 74 65 6e 74 2d 4c 65 |json..Content-Le|
	|00000060| 6e 67 74 68 3a 20 33 30 32 0d 0a 43 6f 6e 6e 65 |ngth: 302..Conne|
	|00000070| 63 74 69 6f 6e 3a 20 6b 65 65 70 2d 61 6c 69 76 |ction: keep-aliv|
	|00000080| 65 0d 0a 41 63 63 65 73 73 2d 43 6f 6e 74 72 6f |e..Access-Contro|
	|00000090| 6c 2d 41 6c 6c 6f 77 2d 4f 72 69 67 69 6e 3a 20 |l-Allow-Origin: |
	|000000a0| 2a 0d 0a 43 61 63 68 65 2d 43 6f 6e 74 72 6f 6c |*..Cache-Control|
	|000000b0| 3a 20 6e 6f 2d 63 61 63 68 65 2c 20 6d 75 73 74 |: no-cache, must|
	|000000c0| 2d 72 65 76 61 6c 69 64 61 74 65 0d 0a 4f 70 65 |-revalidate..Ope|
	|000000d0| 6e 61 69 2d 4d 6f 64 65 6c 3a 20 67 70 74 2d 33 |nai-Model: gpt-3|
	|000000e0| 2e 35 2d 74 75 72 62 6f 2d 30 33 30 31 0d 0a 4f |.5-turbo-0301..O|
	|000000f0| 70 65 6e 61 69 2d 4f 72 67 61 6e 69 7a 61 74 69 |penai-Organizati|
	|00000100| 6f 6e 3a 20 75 73 65 72 2d 6c 72 75 7a 6d 34 76 |on: user-lruzm4v|
	|00000110| 6d 6c 73 6d 76 36 78 65 78 66 72 32 6f 6a 64 61 |mlsmv6xexfr2ojda|
	|00000120| 67 0d 0a 4f 70 65 6e 61 69 2d 50 72 6f 63 65 73 |g..Openai-Proces|
	|00000130| 73 69 6e 67 2d 4d 73 3a 20 33 32 39 0d 0a 4f 70 |sing-Ms: 329..Op|
	|00000140| 65 6e 61 69 2d 56 65 72 73 69 6f 6e 3a 20 32 30 |enai-Version: 20|
	|00000150| 32 30 2d 31 30 2d 30 31 0d 0a 53 74 72 69 63 74 |20-10-01..Strict|
	|00000160| 2d 54 72 61 6e 73 70 6f 72 74 2d 53 65 63 75 72 |-Transport-Secur|
	|00000170| 69 74 79 3a 20 6d 61 78 2d 61 67 65 3d 31 35 37 |ity: max-age=157|
	|00000180| 32 34 38 30 30 3b 20 69 6e 63 6c 75 64 65 53 75 |24800; includeSu|
	|00000190| 62 44 6f 6d 61 69 6e 73 0d 0a 58 2d 52 65 71 75 |bDomains..X-Requ|
	|000001a0| 65 73 74 2d 49 64 3a 20 31 36 37 31 36 31 34 61 |est-Id: 1671614a|
	|000001b0| 65 65 35 37 39 62 64 63 33 39 38 65 32 34 35 38 |ee579bdc398e2458|
	|000001c0| 64 37 39 63 63 65 39 39 0d 0a 0d 0a 7b 22 69 64 |d79cce99....{"id|
	|000001d0| 22 3a 22 63 68 61 74 63 6d 70 6c 2d 36 72 30 57 |":"chatcmpl-6r0W|
	|000001e0| 33 65 4f 5a 79 6a 61 4d 4c 4b 31 51 53 39 52 50 |3eOZyjaMLK1QS9RP|
	|000001f0| 4f 6f 46 47 75 77 68 6f 6c 22 2c 22 6f 62 6a 65 |OoFGuwhol","obje|
	|00000200| 63 74 22 3a 22 63 68 61 74 2e 63 6f 6d 70 6c 65 |ct":"chat.comple|
	|00000210| 74 69 6f 6e 22 2c 22 63 72 65 61 74 65 64 22 3a |tion","created":|
	|00000220| 31 36 37 38 30 39 31 33 38 37 2c 22 6d 6f 64 65 |1678091387,"mode|
	|00000230| 6c 22 3a 22 67 70 74 2d 33 2e 35 2d 74 75 72 62 |l":"gpt-3.5-turb|
	|00000240| 6f 2d 30 33 30 31 22 2c 22 75 73 61 67 65 22 3a |o-0301","usage":|
	|00000250| 7b 22 70 72 6f 6d 70 74 5f 74 6f 6b 65 6e 73 22 |{"prompt_tokens"|
	|00000260| 3a 31 30 34 2c 22 63 6f 6d 70 6c 65 74 69 6f 6e |:104,"completion|
	|00000270| 5f 74 6f 6b 65 6e 73 22 3a 34 2c 22 74 6f 74 61 |_tokens":4,"tota|
	|00000280| 6c 5f 74 6f 6b 65 6e 73 22 3a 31 30 38 7d 2c 22 |l_tokens":108},"|
	|00000290| 63 68 6f 69 63 65 73 22 3a 5b 7b 22 6d 65 73 73 |choices":[{"mess|
	|000002a0| 61 67 65 22 3a 7b 22 72 6f 6c 65 22 3a 22 61 73 |age":{"role":"as|
	|000002b0| 73 69 73 74 61 6e 74 22 2c 22 63 6f 6e 74 65 6e |sistant","conten|
	|000002c0| 74 22 3a 22 5c 6e 5c 6e 48 65 6c 6c 6f 20 57 6f |t":"\n\nHello Wo|
	|000002d0| 72 6c 64 22 7d 2c 22 66 69 6e 69 73 68 5f 72 65 |rld"},"finish_re|
	|000002e0| 61 73 6f 6e 22 3a 22 73 74 6f 70 22 2c 22 69 6e |ason":"stop","in|
	|000002f0| 64 65 78 22 3a 30 7d 5d 7d 0a                   |dex":0}]}.      |
	+--------+-------------------------------------------------+----------------+ 
	*/
	
    interface ChatChoice {
        @JSONField(name = "index")
        public int getIndex();

        @JSONField(name = "index")
        public void setIndex(final int idx);

        // message：
        @JSONField(name = "message")
        public ChatMessage getMessage();

        // message：
        @JSONField(name = "message")
        public void setMessage(final ChatMessage message);

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

        ChatMessage() {
            this._role = null;
            this._content = null;
        }
        
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
