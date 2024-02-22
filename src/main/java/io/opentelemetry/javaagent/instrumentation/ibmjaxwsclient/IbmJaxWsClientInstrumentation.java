/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmjaxwsclient;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.*;
import java.net.InetAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.axis2.context.MessageContext;

public class IbmJaxWsClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return ElementMatchers.named("com.ibm.ws.websvcs.transport.http.SOAPOverHTTPSender");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        ElementMatchers.named("setUserPropertiesInHttpMessage")
            .and(
                ElementMatchers.takesArgument(
                    0, ElementMatchers.named("com.ibm.wsspi.http.channel.HttpRequestMessage"))),
        this.getClass().getName() + "$SetUserPropertiesAdvice");
    typeTransformer.applyAdviceToMethod(
        ElementMatchers.named("send"),
        /*
            .and(
                ElementMatchers.takesArgument(
                    0,
                    ElementMatchers.hasSuperType(
                        ElementMatchers.named(
                            "com.ibm.wsspi.http.channel.outbound.HttpOutboundServiceContext")))),
        */
        this.getClass().getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetUserPropertiesAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0) Object httpReqMsg) {
      HttpRequestMessageHeadersSetter.setter().inject(httpReqMsg);
      return;
    }
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0) MessageContext msgContext,
        @Advice.FieldValue("httpOutSC") Object httpOutSC,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Java8BytecodeBridge.currentContext();

      if (!HttpRequestMessageHeadersSetter.instrumenter().shouldStart(parentContext, msgContext)) {
        return;
      }

      context = HttpRequestMessageHeadersSetter.instrumenter().start(parentContext, msgContext);
      scope = context.makeCurrent();

      if (httpOutSC == null) {
        return;
      }

      try {
        Method method = httpOutSC.getClass().getMethod("getRemotePort");
        Integer remotePort = (Integer) method.invoke(httpOutSC);
        Java8BytecodeBridge.currentSpan().setAttribute("server.port", remotePort);
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }

      try {
        Method method = httpOutSC.getClass().getMethod("getRemoteAddr");
        InetAddress remoteAddr = (InetAddress) method.invoke(httpOutSC);
        Java8BytecodeBridge.currentSpan().setAttribute("server.address", remoteAddr.getHostName());
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) MessageContext msgContext,
        @Advice.Thrown Throwable exception,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      HttpRequestMessageHeadersSetter.instrumenter().end(context, msgContext, null, exception);
    }
  }
}
