/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmjaxwsclient;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This is a demo instrumentation which hooks into servlet invocation and modifies the http
 * response.
 */
@AutoService(InstrumentationModule.class)
public final class IbmJaxWsClientInstrumentationModule extends InstrumentationModule {
  public IbmJaxWsClientInstrumentationModule() {
    super("jaxws-ibm-client", "jaxws-ibm-client-0.1", "jaxws");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("com.example.javaagent.instrumentation");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return AgentElementMatchers.hasClassesNamed(
        "com.ibm.ws.websvcs.transport.http.SOAPOverHTTPSender");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new IbmJaxWsClientInstrumentation());
  }
}
