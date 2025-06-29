package com.chenluo.vulndeserializeccapp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;

@RestController
@RequestMapping("/api")
public class DeserializationController {

    private static final Logger logger = LoggerFactory.getLogger(DeserializationController.class);

    @GetMapping("/")
    public String index() {
        return "<h2>Java Deserialization Vulnerable App (Commons Collections)</h2>" +
               "<p>Send a POST request to /api/deserialize with a Base64 encoded Java serialized object in the request body.</p>" +
               "<p>Use ysoserial to generate a payload, e.g., for CommonsCollections5 (for commons-collections 3.2.1).</p>" +
               "<p>Example (Linux - opens calculator):</p>" +
               "<pre>" +
               "java -jar ysoserial-all.jar CommonsCollections5 'gnome-calculator' | base64 -w 0" +
               "</pre>" +
                "<p>Example (Windows - opens calculator):</p>" +
               "<pre>" +
               "java -jar ysoserial-all.jar CommonsCollections5 'calc.exe' | base64 -w 0" +
               "</pre>";
    }

    @PostMapping("/deserialize")
    public ResponseEntity<String> processSerializedData(@RequestBody String base64Payload) {
        logger.info("Received Base64 payload of length: {}", base64Payload.length());
        try {
            byte[] serializedData = Base64.getDecoder().decode(base64Payload);
            logger.info("Decoded payload to {} bytes.", serializedData.length);

            ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
            ObjectInputStream ois = new ObjectInputStream(bais);

            logger.info("Attempting to deserialize object...");
            Object obj = ois.readObject();
            ois.close();

            String message = "Object deserialized successfully. Object type: " + (obj != null ? obj.getClass().getName() : "null");
            logger.info(message);
            return ResponseEntity.ok(message);

        } catch (Exception e) {
            // *** 关键修改在这里 ***
            // 我们现在使用 logger.error 并将完整的异常 e 作为最后一个参数传入。
            // 这会确保整个堆栈跟踪（包括所有的 "Caused by:"）都被打印出来。
            logger.error("An unexpected error occurred during/after deserialization. Full stack trace:", e);

            // 为了HTTP响应，我们仍然可以返回一个简洁的错误信息
            String rootCauseMessage = e.getMessage();
            Throwable cause = e.getCause();
            while (cause != null) {
                rootCauseMessage = cause.getMessage();
                cause = cause.getCause();
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing serialized data. Root cause: " + rootCauseMessage);
        }
    }
}