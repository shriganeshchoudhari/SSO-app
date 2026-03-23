package com.openidentity.api;

import com.openidentity.api.dto.OrganizationDtos.*;
import com.openidentity.domain.OrganizationEntity;
import com.openidentity.domain.OrganizationMemberEntity;
import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.UserEntity;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin API for organization (tenant) management.
 *
 * Organizations group users and clients within a realm under a shared
 * tenant boundary, enabling delegated administration and per-org policy.
 *
 * Endpoints:
 *   GET    /admin/realms/{realmId}/organizations
 *   POST   /admin/realms/{realmId}/organizations
 *   GET    /admin/realms/{realmId}/organizations/{orgId}
 *   PUT    /admin/realms/{realmId}/organizations/{orgId}
 *   DELETE /admin/realms/{realmId}/organizations/{orgId}
 *   GET    /admin/realms/{realmId}/organizations/{orgId}/members
 *   POST   /admin/realms/{realmId}/organizations/{orgId}/members
 *   DELETE /admin/realms/{realmId}/organizations/{orgId}/members/{memberId}
 */
@Path("/admin/realms/{realmId}/organizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Organizations", description = "Organization / tenant management")
public class AdminOrganizationsResource {

  @Inject EntityManager em;

  // ── Organization CRUD ─────────────────────────────────────────────────────

  @GET
  @Operation(summary = "List organizations in realm")
  public List<OrganizationResponse> list(
      @PathParam("realmId") UUID realmId,
      @QueryParam("first") @DefaultValue("0") int first,
      @QueryParam("max")   @DefaultValue("50") int max) {

    requireRealm(realmId);
    TypedQuery<OrganizationEntity> q = em.createQuery(
        "select o from OrganizationEntity o where o.realm.id = :rid order by o.name",
        OrganizationEntity.class);
    q.setParameter("rid", realmId);
    q.setFirstResult(first);
    q.setMaxResults(max);
    return q.getResultList().stream().map(this::toResponse).collect(Collectors.toList());
  }

  @POST
  @Transactional
  @Operation(summary = "Create organization")
  public Response create(@PathParam("realmId") UUID realmId, CreateOrganizationRequest req) {
    if (req == null || req.name == null || req.name.isBlank()) {
      throw new BadRequestException("name is required");
    }
    RealmEntity realm = requireRealm(realmId);

    boolean exists = !em.createQuery(
            "select o from OrganizationEntity o where o.realm.id = :rid and o.name = :name",
            OrganizationEntity.class)
        .setParameter("rid", realmId)
        .setParameter("name", req.name.trim())
        .setMaxResults(1)
        .getResultList().isEmpty();
    if (exists) {
      throw new ClientErrorException("Organization name already exists in this realm",
          Response.Status.CONFLICT);
    }

    OrganizationEntity org = new OrganizationEntity();
    org.setId(UUID.randomUUID());
    org.setRealm(realm);
    org.setName(req.name.trim());
    org.setDisplayName(req.displayName);
    org.setEnabled(Boolean.TRUE);
    org.setCreatedAt(OffsetDateTime.now());
    em.persist(org);

    return Response
        .created(URI.create("/admin/realms/" + realmId + "/organizations/" + org.getId()))
        .entity(toResponse(org))
        .build();
  }

  @GET
  @Path("/{orgId}")
  @Operation(summary = "Get organization by ID")
  public OrganizationResponse get(
      @PathParam("realmId") UUID realmId,
      @PathParam("orgId")   UUID orgId) {
    return toResponse(requireOrg(realmId, orgId));
  }

  @PUT
  @Path("/{orgId}")
  @Transactional
  @Operation(summary = "Update organization")
  public OrganizationResponse update(
      @PathParam("realmId") UUID realmId,
      @PathParam("orgId")   UUID orgId,
      UpdateOrganizationRequest req) {
    if (req == null) throw new BadRequestException("Request body required");
    OrganizationEntity org = requireOrg(realmId, orgId);
    if (req.displayName != null) org.setDisplayName(req.displayName);
    if (req.enabled     != null) org.setEnabled(req.enabled);
    em.merge(org);
    return toResponse(org);
  }

  @DELETE
  @Path("/{orgId}")
  @Transactional
  @Operation(summary = "Delete organization")
  public Response delete(
      @PathParam("realmId") UUID realmId,
      @PathParam("orgId")   UUID orgId) {
    OrganizationEntity org = requireOrg(realmId, orgId);
    em.remove(org);
    return Response.noContent().build();
  }

  // ── Member management ─────────────────────────────────────────────────────

  @GET
  @Path("/{orgId}/members")
  @Operation(summary = "List organization members")
  public List<MemberResponse> listMembers(
      @PathParam("realmId") UUID realmId,
      @PathParam("orgId")   UUID orgId,
      @QueryParam("first")  @DefaultValue("0") int first,
      @QueryParam("max")    @DefaultValue("50") int max) {

    requireOrg(realmId, orgId);
    return em.createQuery(
            "select m from OrganizationMemberEntity m where m.organization.id = :oid order by m.joinedAt",
            OrganizationMemberEntity.class)
        .setParameter("oid", orgId)
        .setFirstResult(first)
        .setMaxResults(max)
        .getResultList()
        .stream().map(this::toMemberResponse).collect(Collectors.toList());
  }

  @POST
  @Path("/{orgId}/members")
  @Transactional
  @Operation(summary = "Add member to organization")
  public Response addMember(
      @PathParam("realmId") UUID realmId,
      @PathParam("orgId")   UUID orgId,
      AddMemberRequest req) {

    if (req == null || req.userId == null) {
      throw new BadRequestException("userId is required");
    }
    OrganizationEntity org = requireOrg(realmId, orgId);

    UserEntity user = em.find(UserEntity.class, req.userId);
    if (user == null || !user.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("User not found in realm");
    }

    boolean alreadyMember = !em.createQuery(
            "select m from OrganizationMemberEntity m where m.organization.id = :oid and m.user.id = :uid",
            OrganizationMemberEntity.class)
        .setParameter("oid", orgId)
        .setParameter("uid", req.userId)
        .setMaxResults(1)
        .getResultList().isEmpty();
    if (alreadyMember) {
      throw new ClientErrorException("User is already a member of this organization",
          Response.Status.CONFLICT);
    }

    String role = (req.orgRole != null && !req.orgRole.isBlank()) ? req.orgRole.trim() : "member";
    if (!role.equals("member") && !role.equals("admin")) {
      throw new BadRequestException("orgRole must be 'member' or 'admin'");
    }

    OrganizationMemberEntity member = new OrganizationMemberEntity();
    member.setId(UUID.randomUUID());
    member.setOrganization(org);
    member.setUser(user);
    member.setOrgRole(role);
    member.setJoinedAt(OffsetDateTime.now());
    em.persist(member);

    return Response
        .created(URI.create("/admin/realms/" + realmId + "/organizations/" + orgId
            + "/members/" + member.getId()))
        .entity(toMemberResponse(member))
        .build();
  }

  @DELETE
  @Path("/{orgId}/members/{memberId}")
  @Transactional
  @Operation(summary = "Remove member from organization")
  public Response removeMember(
      @PathParam("realmId")  UUID realmId,
      @PathParam("orgId")    UUID orgId,
      @PathParam("memberId") UUID memberId) {

    requireOrg(realmId, orgId);
    OrganizationMemberEntity member = em.find(OrganizationMemberEntity.class, memberId);
    if (member == null || !member.getOrganization().getId().equals(orgId)) {
      throw new NotFoundException("Member not found");
    }
    em.remove(member);
    return Response.noContent().build();
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private RealmEntity requireRealm(UUID realmId) {
    RealmEntity realm = em.find(RealmEntity.class, realmId);
    if (realm == null) throw new NotFoundException("Realm not found");
    return realm;
  }

  private OrganizationEntity requireOrg(UUID realmId, UUID orgId) {
    OrganizationEntity org = em.find(OrganizationEntity.class, orgId);
    if (org == null || !org.getRealm().getId().equals(realmId)) {
      throw new NotFoundException("Organization not found");
    }
    return org;
  }

  private OrganizationResponse toResponse(OrganizationEntity o) {
    return new OrganizationResponse(
        o.getId(), o.getRealm().getId(), o.getName(),
        o.getDisplayName(), o.getEnabled(), o.getCreatedAt());
  }

  private MemberResponse toMemberResponse(OrganizationMemberEntity m) {
    return new MemberResponse(
        m.getId(), m.getOrganization().getId(),
        m.getUser().getId(), m.getOrgRole(), m.getJoinedAt());
  }
}
