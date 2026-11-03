#pragma once
#include "graph_decode.hpp"
#include <dds/dds.h>
namespace pocket {
const dds_topic_descriptor_t* humble_graph_descriptor();
DecodedGraph decode_humble_graph(const void* sample);
}
