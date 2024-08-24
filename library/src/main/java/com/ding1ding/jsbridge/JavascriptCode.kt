package com.ding1ding.jsbridge

object JavascriptCode {
  fun bridge(): String = """
    ;(function (window) {
      if (window.WebViewJavascriptBridge) {
        return;
      }

      const messageHandlers = {};
      const responseCallbacks = {};
      let uniqueId = 1;

      function doSend(message, responseCallback) {
        if (responseCallback) {
          const callbackId = `cb_${'$'}{uniqueId++}_${'$'}{Date.now()}`;
          responseCallbacks[callbackId] = responseCallback;
          message.callbackId = callbackId;
        }
        window.normalPipe.postMessage(JSON.stringify(message));
      }

      function createResponseCallback(handlerName, callbackId) {
        return function (responseData) {
          doSend({ handlerName, responseId: callbackId, responseData });
        };
      }

      window.WebViewJavascriptBridge = {
        registerHandler(handlerName, handler) {
          messageHandlers[handlerName] = handler;
        },

        callHandler(handlerName, data, responseCallback) {
          if (arguments.length === 2 && typeof data === "function") {
            responseCallback = data;
            data = null;
          }
          doSend({ handlerName, data }, responseCallback);
        },

        handleMessageFromNative(messageJSON) {
          const message = JSON.parse(messageJSON);

          if (message.responseId) {
            const responseCallback = responseCallbacks[message.responseId];
            if (responseCallback) {
              responseCallback(message.responseData);
              delete responseCallbacks[message.responseId];
            }
            return;
          }

          let responseCallback;
          if (message.callbackId) {
            responseCallback = createResponseCallback(message.handlerName, message.callbackId);
          }

          const handler = messageHandlers[message.handlerName];
          if (handler) {
            handler(message.data, responseCallback);
          } else {
            console.warn("WebViewJavascriptBridge: No handler for message from Native:", message);
          }
        },
      };
    })(window);
  """.trimIndent()

  fun hookConsole(): String = """
    ;(function (window) {
      if (window.isConsoleHooked) {
        console.log("Console hook has already been applied.");
        return;
      }

      function printObject(obj) {
        if (obj === null) return "null";
        if (typeof obj === "undefined") return "undefined";
        if (obj instanceof Promise) return "This is a javascript Promise.";
        if (obj instanceof Date) return obj.getTime().toString();
        if (Array.isArray(obj)) return `[${'$'}{obj.toString()}]`;
        if (typeof obj === "object") {
          const entries = Object.entries(obj).map(([key, value]) => `"${'$'}{key}":"${'$'}{value}"`);
          return `{${'$'}{entries.join(",")}}`;
        }
        return String(obj);
      }

      console.log("Starting console hook application.");

      const originalConsoleLog = window.console.log;

      window.console.log = function (...args) {
        window.isConsoleHooked = true;
        args.forEach(obj => {
          originalConsoleLog.call(window.console, obj);
          const message = printObject(obj);
          window.consolePipe.receiveConsole(message);
        });
      };

      console.log("Console hook has been applied successfully.");
    })(window);
  """.trimIndent()
}
