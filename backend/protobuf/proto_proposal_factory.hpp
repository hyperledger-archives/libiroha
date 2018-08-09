/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef IROHA_PROTO_PROPOSAL_FACTORY_HPP
#define IROHA_PROTO_PROPOSAL_FACTORY_HPP

#include "backend/protobuf/proposal.hpp"
#include "interfaces/iroha_internal/proposal_factory.hpp"
#include "interfaces/iroha_internal/unsafe_proposal_factory.hpp"
#include "proposal.pb.h"

namespace shared_model {
  namespace proto {
    template <typename Validator>
    class ProtoProposalFactory : public interface::ProposalFactory,
                                 public interface::UnsafeProposalFactory {
     public:
      FactoryResult<std::unique_ptr<interface::Proposal>> createProposal(
          interface::types::HeightType height,
          interface::types::TimestampType created_time,
          const interface::types::TransactionsCollectionType &transactions)
          override {
        return createProposal(
            createProtoProposal(height, created_time, transactions));
      }

      std::unique_ptr<interface::Proposal> unsafeCreateProposal(
          interface::types::HeightType height,
          interface::types::TimestampType created_time,
          const interface::types::TransactionsCollectionType &transactions)
          override {
        return std::make_unique<Proposal>(
            createProtoProposal(height, created_time, transactions));
      }

      /**
       * Create and validate proposal using protobuf object
       */
      FactoryResult<std::unique_ptr<interface::Proposal>> createProposal(
          const iroha::protocol::Proposal &proposal) {
        return validate(std::make_unique<Proposal>(proposal));
      }

     private:
      iroha::protocol::Proposal createProtoProposal(
          interface::types::HeightType height,
          interface::types::TimestampType created_time,
          const interface::types::TransactionsCollectionType &transactions) {
        iroha::protocol::Proposal proposal;

        proposal.set_height(height);
        proposal.set_created_time(created_time);

        for (const auto &tx : transactions) {
          *proposal.add_transactions() =
              static_cast<const shared_model::proto::Transaction &>(tx)
                  .getTransport();
        }

        return proposal;
      }

      FactoryResult<std::unique_ptr<interface::Proposal>> validate(
          std::unique_ptr<Proposal> proposal) {
        auto errors = validator_.validate(*proposal);

        if (errors) {
          return iroha::expected::makeError(errors.reason());
        }

        return iroha::expected::makeValue<std::unique_ptr<interface::Proposal>>(
            std::move(proposal));
      }

      Validator validator_;
    };
  }  // namespace proto
}  // namespace shared_model

#endif  // IROHA_PROTO_PROPOSAL_FACTORY_HPP
