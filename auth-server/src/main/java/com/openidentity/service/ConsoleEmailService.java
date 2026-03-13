package com.openidentity.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ConsoleEmailService implements EmailService {
  private static final Logger LOG = Logger.getLogger(ConsoleEmailService.class);

  @Override
  public void send(String to, String subject, String body) {
    // Dev/MVP implementation: log outgoing email content.
    LOG.infof("EMAIL to=%s subject=%s body=%s", to, subject, body);
  }
}

