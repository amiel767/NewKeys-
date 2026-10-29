// Dumps one held-then-released note to WAV for spectral analysis (pitch + brightness).
#include "aetherfx/fx_sax.h"
#include "presets_sax.h"
#include <cstdio>
#include <cstdlib>
#include <cmath>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>
using namespace aetherfx;
static const int SR=48000, BLK=256;
static std::vector<uint8_t> slurp(const std::string& p){FILE*f=fopen(p.c_str(),"rb");if(!f){fprintf(stderr,"open %s\n",p.c_str());exit(1);}fseek(f,0,SEEK_END);long n=ftell(f);fseek(f,0,SEEK_SET);std::vector<uint8_t> v(n);if(fread(v.data(),1,n,f)!=(size_t)n)exit(1);fclose(f);return v;}
static void writeWav(const std::string& p,const std::vector<float>&lr){
  const uint32_t fr=(uint32_t)(lr.size()/2), db=fr*2*4; FILE*f=fopen(p.c_str(),"wb");
  auto u32=[&](uint32_t v){fwrite(&v,4,1,f);}; auto u16=[&](uint16_t v){fwrite(&v,2,1,f);};
  fwrite("RIFF",1,4,f);u32(36+db);fwrite("WAVE",1,4,f);fwrite("fmt ",1,4,f);u32(16);u16(3);u16(2);u32(SR);u32(SR*8);u16(8);u16(32);
  fwrite("data",1,4,f);u32(db);fwrite(lr.data(),4,lr.size(),f);fclose(f);
}
int main(int argc,char**argv){
  const char* presetName = argc>1?argv[1]:"Late Night Tenor";
  float fb = argc>2?atof(argv[2]):0.0f;
  const char* out = argc>3?argv[3]:"/tmp/saxdemo/fall.wav";
  const SaxPreset* pr=nullptr;
  for(int i=0;i<SAX_PRESET_COUNT;i++) if(std::string(SAX_PRESETS[i].name)==presetName) pr=&SAX_PRESETS[i];
  if(!pr){fprintf(stderr,"preset? %s\n",presetName);return 1;}
  float P[22]; memcpy(P,pr->v,sizeof(P)); P[11]=fb;
  const char* bank = P[0]<0.165f?"saxpack_sop.bin":P[0]<0.5f?"saxpack_alt.bin":P[0]<0.835f?"saxpack_ten.bin":"saxpack_bar.bin";
  auto bytes=slurp(std::string("/workspace/project/NewKeys-/app/src/main/assets/saxpack/")+bank);
  SaxEngine s; s.prepare(SR,BLK); s.loadSamples(bytes.data(),bytes.size()); s.setSampleMix(1.f);
  for(int i=0;i<22;i++) s.setNorm(i,P[i]);
  std::vector<float> b(BLK*2), outv;
  auto pump=[&](float sec){ int nb=(int)std::ceil(sec*SR/BLK); for(int k=0;k<nb;k++){ std::fill(b.begin(),b.end(),0.f); s.process(b.data(),BLK); for(int j=0;j<BLK;j++){outv.push_back(b[2*j]);outv.push_back(b[2*j+1]);} } };
  s.noteOn(62,105);           // D4, mid-range for a tenor
  pump(0.8f);
  s.noteOff(62);
  pump(1.5f);
  writeWav(out,outv);
  printf("%s  fall=%.2f  -> %s (%zu frames)\n",presetName,fb,out,outv.size()/2);
  return 0;
}
