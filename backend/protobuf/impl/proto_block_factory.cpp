/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include "backend/protobuf/proto_block_factory.hpp"

using namespace shared_model::proto;

ProtoBlockFactory::ProtoBlockFactory(
    std::unique_ptr<shared_model::validation::AbstractValidator<
        shared_model::interface::BlockVariant>> validator)
    : validator_(std::move(validator)){};

shared_model::interface::BlockVariant ProtoBlockFactory::unsafeCreateBlock(
    interface::types::HeightType height,
    const interface::types::HashType &prev_hash,
    interface::types::TimestampType created_time,
    const interface::types::TransactionsCollectionType &txs) {
  iroha::protocol::Block block;
  auto *block_payload = block.mutable_payload();
  block_payload->set_height(height);
  block_payload->set_prev_block_hash(crypto::toBinaryString(prev_hash));
  block_payload->set_created_time(created_time);

  if (not txs.empty()) {
    std::for_each(
        std::begin(txs), std::end(txs), [&block_payload](const auto &tx) {
          auto *transaction = block_payload->add_transactions();
          (*transaction) = static_cast<const Transaction &>(tx).getTransport();
        });
    return std::make_shared<shared_model::proto::Block>(std::move(block));
  }

  return std::make_shared<shared_model::proto::EmptyBlock>(std::move(block));
}

iroha::expected::Result<shared_model::interface::BlockVariant, std::string>
ProtoBlockFactory::createBlock(iroha::protocol::Block block) {
  interface::BlockVariant proto_block;
  if (block.payload().transactions().empty()) {
    proto_block = std::make_shared<EmptyBlock>(std::move(block));
  } else {
    proto_block = std::make_shared<Block>(std::move(block));
  }
  auto errors = validator_->validate(proto_block);
  if (errors) {
    return iroha::expected::makeError(errors.reason());
  }
  return iroha::expected::makeValue(std::move(proto_block));
}
