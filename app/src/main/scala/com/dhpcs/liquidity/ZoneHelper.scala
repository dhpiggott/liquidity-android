package com.dhpcs.liquidity

import com.dhpcs.liquidity.models._

import scala.collection.JavaConversions._

object ZoneHelper {

  def aggregateMembersAccountBalancesAsJavaMap(zone: Zone,
                                               accountBalances: Map[AccountId, BigDecimal]) =
    mapAsJavaMap(
      aggregateMembersAccountBalances(
        zone,
        accountBalances
      ).map {
        case (memberId, balance) => memberId -> balance.bigDecimal
      }
    )

  def aggregateMembersAccountBalances(zone: Zone,
                                      accountBalances: Map[AccountId, BigDecimal]) =
    zone.members.map {
      case (memberId, _) =>
        val membersAccounts = zone.accounts.filter {
          case (_, account) =>
            account.owners.contains(memberId)
        }
        val membersAccountBalances = membersAccounts.map {
          case (accountId, _) =>
            accountId -> accountBalances.getOrElse(accountId, BigDecimal(0))
        }
        memberId -> membersAccountBalances.values.sum
    }

  def clientMembersAsJavaMap(zone: Zone, clientPublicKey: PublicKey) =
    mapAsJavaMap(
      clientMembers(
        zone,
        clientPublicKey
      )
    )

  def clientMembers(zone: Zone, clientPublicKey: PublicKey) =
    zone.members.filter {
      case (memberId, member) =>
        memberId != zone.equityHolderMemberId && member.publicKey == clientPublicKey
    }

  def connectedMembersAsJavaMap(members: java.util.Map[MemberId, Member],
                                connectedClients: java.util.Set[PublicKey]) =
    mapAsJavaMap(
      connectedMembers(
        mapAsScalaMap(members).toMap,
        asScalaSet(connectedClients).toSet
      )
    )

  def connectedMembers(members: Map[MemberId, Member], connectedClients: Set[PublicKey]) =
    members.filter {
      case (_, member) =>
        connectedClients.contains(member.publicKey)
    }

  def otherMembersAsJavaMap(zone: Zone, userPublicKey: PublicKey) =
    mapAsJavaMap(
      otherMembers(
        zone,
        userPublicKey
      )
    )

  def otherMembers(zone: Zone, userPublicKey: PublicKey) =
    zone.members.filter {
      case (_, member) =>
        member.publicKey != userPublicKey
    }

}