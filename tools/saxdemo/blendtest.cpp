// End-to-end check of the Blend knob: parameter 22 must actually change the output.
// Renders the same preset at blend 0 / 0.5 / 1 and reports RMS + the difference to blend 0.
#include "aetherfx/fx_sax.h"
#include "presets_sax.h"
#include <cstdio>
#include <cmath>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>
using namespace aetherfx;
static const int SR=48000, BLK=256;
static std::vector<uint8_t> slurp(const std::string& p){FILE*f=fopen(p.c_str(),"rb");if(!f)exit(1);fseek(f,0,SEEK_END);long n=ftell(f);fseek(f,0,SEEK_SET);std::vector<uint8_t> v(n);size_t r=fread(v.data(),1,n,f);(void)r;fclose(f);return v;}
static std::vector<float> run(const std::vector<uint8_t>& bin, const float* P, float mix){
  SaxEngine s; s.prepare(SR,BLK); s.loadSamples(bin.data(),bin.size());
  for(int i=0;i<23;i++) s.setNorm(i,P[i]);
  s.setNorm(22,mix);
  std::vector<float> b(BLK*2), out;
  s.noteOn(62,105);
  for(int k=0;k<100;k++){ std::fill(b.begin(),b.end(),0.f); s.process(b.data(),BLK); for(int j=0;j<BLK;j++) out.push_back(b[2*j]); }
  return out;
}
int main(){
  const SaxPreset* pr=&SAX_PRESETS[0];
  for(int i=0;i<SAX_PRESET_COUNT;i++) if(std::string(SAX_PRESETS[i].name)=="Late Night Tenor") pr=&SAX_PRESETS[i];
  auto bin=slurp("/workspace/project/NewKeys-/app/src/main/assets/saxpack/saxpack_ten.bin");
  auto a=run(bin,pr->v,0.f), m=run(bin,pr->v,0.5f), z=run(bin,pr->v,1.f);
  auto rms=[](const std::vector<float>&x){ double e=0; for(float v:x) e+=v*v; return std::sqrt(e/x.size()); };
  auto diff=[](const std::vector<float>&x,const std::vector<float>&y){ double e=0; for(size_t i=0;i<x.size()&&i<y.size();i++){double d=x[i]-y[i]; e+=d*d;} return std::sqrt(e/x.size()); };
  printf("preset '%s' (blend du preset = %.2f)\n", pr->name, pr->v[22]);
  printf("  blend 0.0 (synthese pure) : rms %.4f\n", rms(a));
  printf("  blend 0.5 (preset)        : rms %.4f   ecart a 0.0 : %.4f\n", rms(m), diff(m,a));
  printf("  blend 1.0 (echantillon)   : rms %.4f   ecart a 0.0 : %.4f\n", rms(z), diff(z,a));
  printf("  => le parametre 22 change bien la sortie : %s\n", (diff(m,a)>0.01 && diff(z,a)>0.01) ? "OUI" : "NON");
  return 0;
}
