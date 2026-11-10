#pragma once
#include "graph_decode.hpp"
#include <dds/dds.h>
namespace pocket {
const dds_topic_descriptor_t* humble_graph_descriptor();
DecodedGraph decode_humble_graph(const void* sample);
bool write_humble_reader_announcement(dds_entity_t writer,const dds_guid_t& participant,const std::string& name,const std::vector<dds_guid_t>& readers);
}
