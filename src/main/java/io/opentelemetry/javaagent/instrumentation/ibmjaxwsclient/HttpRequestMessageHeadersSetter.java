/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmjaxwsclient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.lang.reflect.Method;
import org.apache.axis2.context.MessageContext;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpRequestMessageHeadersSetter {
  private static final HttpRequestMessageHeadersSetter SETTER;
  private final ContextPropagators contextPropagators;

  private HttpRequestMessageHeadersSetter(ContextPropagators contextPropagators) {
    this.contextPropagators = contextPropagators;
  }

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jaxws-ibm-client-0.1";

  private static final Instrumenter<MessageContext, Void> INSTRUMENTER;

  static {
    InstrumenterBuilder<MessageContext, Void> builder =
        Instrumenter.<MessageContext, Void>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, new MySpanNameExtractor());

    /*
    HttpSpanNameExtractor.builder(httpAttributesGetter)
        .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
        .build())
     */
    // .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
    /*
    .addAttributesExtractor(
        HttpClientAttributesExtractor.builder(httpAttributesGetter)
            .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
            .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
            .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
            .build())
    .addAttributesExtractor(
        HttpClientPeerServiceAttributesExtractor.create(
            httpAttributesGetter, CommonConfig.get().getPeerServiceResolver()))
    .addOperationMetrics(HttpClientMetrics.get());
     */
    /*
    if (CommonConfig.get().shouldEmitExperimentalHttpClientTelemetry()) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpClientExperimentalMetrics.get());
    }
    INSTRUMENTER = builder.buildClientInstrumenter(HttpHeaderSetter.INSTANCE);
     */
    INSTRUMENTER = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<MessageContext, Void> instrumenter() {
    return INSTRUMENTER;
  }

  // Object is com.ibm.wsspi.http.channel.HttpRequestMessage
  public void inject(Object httpReqMsg) {
    contextPropagators
        .getTextMapPropagator()
        .inject(
            Context.current(),
            httpReqMsg,
            (carrier, key, value) -> {
              if (carrier != null) {
                try {
                  Class[] argTypes = new Class[] {String.class, String.class};
                  Method method = carrier.getClass().getMethod("setHeader", argTypes);
                  method.invoke(carrier, key, value);
                } catch (Throwable t) {
                  // ignore
                }
              }
            });

    return;
  }

  static {
    SETTER = new HttpRequestMessageHeadersSetter(GlobalOpenTelemetry.getPropagators());
  }

  public static HttpRequestMessageHeadersSetter setter() {
    return SETTER;
  }

  private static class MySpanNameExtractor implements SpanNameExtractor<MessageContext> {
    @Override
    public String extract(MessageContext msgContext) {
      return msgContext.getAxisService().toString()
          + "/"
          + msgContext.getAxisOperation().getSoapAction();
    }
  }
}
