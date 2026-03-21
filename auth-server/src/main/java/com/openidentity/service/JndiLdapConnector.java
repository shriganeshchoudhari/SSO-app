package com.openidentity.service;

import com.openidentity.domain.LdapProviderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

@ApplicationScoped
public class JndiLdapConnector implements LdapConnector {
  @Override
  public LdapFederationService.LdapAuthenticationOutcome authenticate(
      LdapProviderEntity provider,
      String username,
      String password,
      String bindCredential) {
    if (password == null || password.isBlank()) {
      return LdapFederationService.LdapAuthenticationOutcome.invalidCredentials();
    }
    LdapFederationService.LdapLookupOutcome lookup = lookupUser(provider, username, bindCredential);
    if (lookup.status() != LdapFederationService.LdapLookupStatus.FOUND || lookup.user() == null) {
      return switch (lookup.status()) {
        case NOT_FOUND -> LdapFederationService.LdapAuthenticationOutcome.userNotFound();
        case UNAVAILABLE -> LdapFederationService.LdapAuthenticationOutcome.unavailable();
        default -> LdapFederationService.LdapAuthenticationOutcome.invalidCredentials();
      };
    }
    String userDn = lookup.distinguishedName();
    if (userDn == null || userDn.isBlank()) {
      return LdapFederationService.LdapAuthenticationOutcome.unavailable();
    }
    DirContext userContext = null;
    try {
      userContext = openUserContext(provider, userDn, password);
      return LdapFederationService.LdapAuthenticationOutcome.authenticated(lookup.user());
    } catch (NamingException e) {
      return LdapFederationService.LdapAuthenticationOutcome.invalidCredentials();
    } catch (Exception e) {
      return LdapFederationService.LdapAuthenticationOutcome.unavailable();
    } finally {
      closeQuietly(userContext);
    }
  }

  @Override
  public LdapFederationService.LdapLookupOutcome lookupUser(
      LdapProviderEntity provider,
      String username,
      String bindCredential) {
    if (username == null || username.isBlank()) {
      return LdapFederationService.LdapLookupOutcome.notFound();
    }

    String usernameAttribute = provider.getUsernameAttribute() != null && !provider.getUsernameAttribute().isBlank()
        ? provider.getUsernameAttribute()
        : "uid";
    String filter = provider.getUserSearchFilter() != null && !provider.getUserSearchFilter().isBlank()
        ? provider.getUserSearchFilter()
        : "(" + usernameAttribute + "={0})";
    String searchBase = provider.getUserSearchBase() != null ? provider.getUserSearchBase() : "";

    DirContext serviceContext = null;
    try {
      serviceContext = openServiceContext(provider, bindCredential);
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      controls.setReturningAttributes(returningAttributes(provider));

      NamingEnumeration<SearchResult> results = serviceContext.search(searchBase, filter, new Object[] { username }, controls);
      try {
        if (!results.hasMore()) {
          return LdapFederationService.LdapLookupOutcome.notFound();
        }
        SearchResult result = results.next();
        String userDn = result.getNameInNamespace();
        Attributes attributes = result.getAttributes();
        String resolvedUsername = readAttribute(attributes, usernameAttribute);
        String resolvedEmail = readAttribute(attributes, provider.getEmailAttribute());
        return LdapFederationService.LdapLookupOutcome.found(
            new LdapFederationService.LdapDirectoryUser(
                resolvedUsername != null && !resolvedUsername.isBlank() ? resolvedUsername : username,
                resolvedEmail),
            userDn);
      } finally {
        results.close();
      }
    } catch (NamingException e) {
      return LdapFederationService.LdapLookupOutcome.unavailable();
    } catch (Exception e) {
      return LdapFederationService.LdapLookupOutcome.unavailable();
    } finally {
      closeQuietly(serviceContext);
    }
  }

  private DirContext openServiceContext(LdapProviderEntity provider, String bindCredential) throws Exception {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, provider.getUrl());
    if (provider.getBindDn() != null && !provider.getBindDn().isBlank()) {
      env.put(Context.SECURITY_AUTHENTICATION, "simple");
      env.put(Context.SECURITY_PRINCIPAL, provider.getBindDn());
      env.put(Context.SECURITY_CREDENTIALS, bindCredential != null ? bindCredential : "");
    } else {
      env.put(Context.SECURITY_AUTHENTICATION, "none");
    }
    return new InitialDirContext(env);
  }

  private DirContext openUserContext(LdapProviderEntity provider, String userDn, String password) throws Exception {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, provider.getUrl());
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, userDn);
    env.put(Context.SECURITY_CREDENTIALS, password);
    return new InitialDirContext(env);
  }

  private String[] returningAttributes(LdapProviderEntity provider) {
    Set<String> attributes = new LinkedHashSet<>();
    if (provider.getUsernameAttribute() != null && !provider.getUsernameAttribute().isBlank()) {
      attributes.add(provider.getUsernameAttribute());
    }
    if (provider.getEmailAttribute() != null && !provider.getEmailAttribute().isBlank()) {
      attributes.add(provider.getEmailAttribute());
    }
    if (attributes.isEmpty()) {
      return null;
    }
    return attributes.toArray(String[]::new);
  }

  private String readAttribute(Attributes attributes, String name) throws Exception {
    if (attributes == null || name == null || name.isBlank()) {
      return null;
    }
    Attribute attribute = attributes.get(name);
    if (attribute == null || attribute.size() == 0) {
      return null;
    }
    Object value = attribute.get();
    return value != null ? String.valueOf(value) : null;
  }

  private void closeQuietly(DirContext context) {
    if (context == null) {
      return;
    }
    try {
      context.close();
    } catch (Exception ignored) {
      // ignore close failures for best-effort cleanup
    }
  }
}
