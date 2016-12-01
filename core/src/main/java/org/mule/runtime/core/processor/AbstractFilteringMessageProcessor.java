/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.processor;

import static org.mule.runtime.core.util.rx.Exceptions.newEventDroppedException;
import static reactor.core.Exceptions.propagate;
import static reactor.core.publisher.Flux.error;
import static reactor.core.publisher.Flux.from;
import static reactor.core.publisher.Flux.just;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.Event.Builder;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.processor.InterceptingMessageProcessor;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.routing.filter.FilterUnacceptedException;
import org.mule.runtime.core.config.i18n.CoreMessages;
import org.mule.runtime.core.exception.MessagingException;

import java.util.function.Function;

import org.reactivestreams.Publisher;

/**
 * Abstract {@link InterceptingMessageProcessor} that can be easily be extended and used for filtering message flow through a
 * {@link Processor} chain. The default behaviour when the filter is not accepted is to return the request event.
 */
public abstract class AbstractFilteringMessageProcessor extends AbstractInterceptingMessageProcessor {

  /**
   * Throw a FilterUnacceptedException when a message is rejected by the filter?
   */
  protected boolean throwOnUnaccepted = false;
  protected boolean onUnacceptedFlowConstruct;


  /**
   * The <code>MessageProcessor</code> that should be used to handle messages that are not accepted by the filter.
   */
  protected Processor unacceptedMessageProcessor;

  @Override
  public Event process(Event event) throws MuleException {
    boolean accepted;
    Builder builder = Event.builder(event);
    try {
      accepted = accept(event, builder);
    } catch (Exception ex) {
      throw filterFailureException(builder.build(), ex);
    }
    if (accepted) {
      return processNext(builder.build());
    } else {
      return handleUnaccepted(builder.build());
    }
  }

  @Override
  public Publisher<Event> apply(Publisher<Event> publisher) {
    return from(publisher).concatMap(event -> {
      Builder builder = Event.builder(event);
      boolean accepted = accept(event, builder);
      if (accepted) {
        return applyNext(just(builder.build()));
      } else {
        return handleUnaccepted().apply(event);
      }
    });
  }

  protected abstract boolean accept(Event event, Event.Builder builder);

  protected Event handleUnaccepted(Event event) throws MuleException {
    if (unacceptedMessageProcessor != null) {
      return unacceptedMessageProcessor.process(event);
    } else if (isThrowOnUnaccepted()) {
      throw filterUnacceptedException(event);
    } else {
      return null;
    }
  }

  private Function<Event, Publisher<Event>> handleUnaccepted() {
    if (unacceptedMessageProcessor != null) {
      return event -> just(event).transform(unacceptedMessageProcessor);
    } else if (isThrowOnUnaccepted()) {
      return event -> {
        throw propagate(filterUnacceptedException(event));
      };
    } else {
      return event -> error(newEventDroppedException(event));
    }
  }

  protected MessagingException filterFailureException(Event event, Exception ex) {
    return new MessagingException(event, ex, this);
  }

  protected MuleException filterUnacceptedException(Event event) {
    return new FilterUnacceptedException(CoreMessages.messageRejectedByFilter());
  }

  public Processor getUnacceptedMessageProcessor() {
    return unacceptedMessageProcessor;
  }

  public void setUnacceptedMessageProcessor(Processor unacceptedMessageProcessor) {
    this.unacceptedMessageProcessor = unacceptedMessageProcessor;
    if (unacceptedMessageProcessor instanceof FlowConstruct) {
      onUnacceptedFlowConstruct = true;
    }
  }

  public boolean isThrowOnUnaccepted() {
    return throwOnUnaccepted;
  }

  public void setThrowOnUnaccepted(boolean throwOnUnaccepted) {
    this.throwOnUnaccepted = throwOnUnaccepted;
  }
}
