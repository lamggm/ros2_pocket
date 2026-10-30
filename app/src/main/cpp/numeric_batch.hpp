#pragma once
#include "structured.hpp"
#include "model.hpp"
#include <array>
#include <cstdint>
#include <cmath>
#include <iomanip>
#include <locale>
#include <sstream>
#include <string>
#include <vector>

namespace pocket {
/** Selected numeric values only; fixed records, latest-preserving local overflow. */
class NumericBatch {
 struct Sample {
  uint64_t sequence=0;int64_t received=0,source=0;
  std::array<double,4> values{};uint8_t finite=0;
 };
 static_assert(sizeof(Sample)<=64,"numeric record budget");
 std::array<Sample,32> records{};size_t head=0,size=0;uint64_t dropped=0;
 static const Field* leaf(const Field& field,const std::string& path){
  if(field.kind=="number"&&field.path==path)return &field;
  for(const auto& child:field.children)if(const auto* found=leaf(child,path))return found;
  return nullptr;
 }
 public:
 std::vector<std::string> paths;
 void consume(){head=0;size=0;}
 void configure(std::vector<std::string> selected){if(paths!=selected){paths=std::move(selected);consume();}}
 void reset_reader(){consume();dropped=0;}
 void discard_pending(){dropped+=size;consume();}
 void push(uint64_t sequence,int64_t received,int64_t source,const Field& tree){
  if(paths.empty())return;
  Sample sample;sample.sequence=sequence;sample.received=received;sample.source=source;
  for(size_t i=0;i<paths.size();i++){
   const auto* field=leaf(tree,paths[i]);if(!field||field->json_value=="null")continue;
   std::istringstream number(field->json_value);number.imbue(std::locale::classic());double value=0;number>>value;
   if(!number.fail()&&number.eof()&&std::isfinite(value)){sample.values[i]=value;sample.finite|=uint8_t(1u<<i);}
  }
  if(size<records.size()){records[(head+size)%records.size()]=sample;++size;}
  else{records[head]=sample;head=(head+1)%records.size();++dropped;}
 }
 std::string json(int slot,uint64_t generation) const {
  std::ostringstream out;out.imbue(std::locale::classic());out<<std::setprecision(17);
  out<<"{\"slot\":"<<slot<<",\"readerGeneration\":"<<generation<<",\"paths\":[";
  for(size_t i=0;i<paths.size();i++){if(i)out<<',';out<<json_quote(paths[i]);}
  out<<"],\"samples\":[";
  for(size_t i=0;i<size;i++){
   const auto& sample=records[(head+i)%records.size()];if(i)out<<',';
   out<<'['<<sample.sequence<<','<<sample.received<<','<<sample.source;
   for(size_t p=0;p<paths.size();p++){out<<',';if(sample.finite&(1u<<p))out<<sample.values[p];else out<<"null";}
   out<<']';
  }
  out<<"],\"droppedLocal\":"<<dropped<<'}';return out.str();
 }
};
}
