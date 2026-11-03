#include "graph_humble.hpp"
#include "graph_humble_aliases.h"
#include "GraphHumble.h"
namespace pocket {
const dds_topic_descriptor_t* humble_graph_descriptor(){return &pocket_humble24_Participant_desc;}
DecodedGraph decode_humble_graph(const void* sample){return decode_graph(static_cast<const pocket_humble24_Participant*>(sample));}
}
