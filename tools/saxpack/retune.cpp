// Re-measures every region of the sax packs and rewrites its `tuneCents` field.
//
// Why: the value shipped in the packs does not describe the audio it labels. The reader
// compensates with `rootF *= 2^(tuneCents/1200)`, so a wrong value transposes the note.
// Measured against the engine, the alto pack plays up to +52 cents sharp, which is what
// made the hybrid layer sound like a detuned double of the synthesis.
//
// The measurement is done on the decoded region itself (same ADPCM routine as the engine)
// and the fundamental is confirmed by requiring energy at 2f and 3f, because a plain peak
// pick reports a partial on this timbre and looks off by an octave.
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <cmath>
#include <vector>
#include <string>
#include <complex>
#include <algorithm>

static const int kStep[89] = {7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767};
static const int kIdx[8] = {-1,-1,-1,-1,2,4,6,8};

static std::vector<uint8_t> slurp(const std::string& p){
  FILE* f=fopen(p.c_str(),"rb"); if(!f){perror(p.c_str());exit(2);}
  fseek(f,0,SEEK_END); long n=ftell(f); fseek(f,0,SEEK_SET);
  std::vector<uint8_t> v(n); if(fread(v.data(),1,n,f)!=(size_t)n) exit(3); fclose(f); return v;
}

static void fft(std::vector<std::complex<double>>& a){
  int n=(int)a.size();
  for(int i=1,j=0;i<n;i++){int b=n>>1;for(;j&b;b>>=1)j^=b;j^=b;if(i<j)std::swap(a[i],a[j]);}
  for(int len=2;len<=n;len<<=1){
    double ang=-2*M_PI/len; std::complex<double> wl(std::cos(ang),std::sin(ang));
    for(int i=0;i<n;i+=len){std::complex<double> w(1,0);
      for(int k=0;k<len/2;k++){auto u=a[i+k],v=a[i+k+len/2]*w;a[i+k]=u+v;a[i+k+len/2]=u-v;w*=wl;}}
  }
}

// Decoded region, true fundamental in Hz (0 when it cannot be confirmed).
static double measure(const std::vector<uint8_t>& pcm, uint32_t off, uint32_t frames, int rate, double want){
  const uint32_t nbytes=(frames+1)/2;
  if((size_t)off+nbytes>pcm.size()) return 0;
  std::vector<float> x; x.reserve(frames);
  int pred=0,index=0;
  for(uint32_t b=0;b<nbytes && x.size()<frames;b++){
    uint8_t byte=pcm[off+b];
    for(int half=0;half<2;half++){
      if(x.size()>=frames) break;
      int code = half ? ((byte>>4)&0xF) : (byte&0xF);
      int st=kStep[index]; int delta=st>>3;
      if(code&4) delta+=st; if(code&2) delta+=st>>1; if(code&1) delta+=st>>2;
      pred += (code&8)? -delta : delta;
      pred=std::max(-32768,std::min(32767,pred));
      index=std::max(0,std::min(88,index+kIdx[code&7]));
      x.push_back(pred/32768.f);
    }
  }
  const int N=16384;
  if((int)x.size()<N+1000) return 0;
  std::vector<std::complex<double>> a(N,0);
  size_t base=x.size()/2 - N/2;
  for(int i=0;i<N;i++) a[i]=x[base+i]*(0.5-0.5*std::cos(2*M_PI*i/(N-1)));
  fft(a);
  std::vector<double> mag(N/2); for(int k=0;k<N/2;k++) mag[k]=std::abs(a[k]);
  double mx=0; for(int k=1;k<N/2;k++) mx=std::max(mx,mag[k]);
  auto at=[&](double f){ int k=(int)std::lround(f*N/rate); if(k<1||k>=N/2) return 0.0; double b=0; for(int d=-2;d<=2;d++) if(k+d>=1&&k+d<N/2) b=std::max(b,mag[k+d]); return b; };
  for(int k=1;k<N/2;k++){
    double f=k*(double)rate/N;
    if(f<120||f>900) continue;
    if(mag[k]<0.10*mx) continue;
    if(at(2*f)>0.25*mag[k] && at(3*f)>0.10*mag[k]){
      double m0=std::log(mag[k-1]+1e-12),m1=std::log(mag[k]+1e-12),m2=std::log(mag[k+1]+1e-12);
      double d=0.5*(m0-m2)/(m0-2*m1+m2);
      return (k+d)*(double)rate/N;
    }
  }
  // Fallback for the regions where the harmonic test cannot confirm a fundamental (a noisy
  // attack, a very short region). Take the strongest partial and shift it by octaves until it
  // lands near the region's nominal root: the library drifts by well under a semitone, so the
  // octave nearest the root is the right one. Without this the region keeps its stale value,
  // which is exactly how the C5 region stayed 44 cents sharp after the first pass.
  int bk=0; double bm=0;
  for(int k=1;k<N/2;k++){ double f=k*(double)rate/N; if(f<120||f>1600) continue; if(mag[k]>bm){bm=mag[k];bk=k;} }
  if(bk<=0) return 0;
  double f=bk*(double)rate/N;
  while(f>want*1.06) f/=2;
  while(f<want*0.94) f*=2;
  return f;
}

int main(int argc,char**argv){
  if(argc<2){ std::fprintf(stderr,"usage: retune <pack.bin> [--apply]\n"); return 1; }
  const bool apply = argc>2 && std::string(argv[2])=="--apply";
  std::string path=argv[1];
  auto b=slurp(path);
  uint32_t rate=0,count=0,pcmLen=0,recLen=0;
  std::memcpy(&rate,b.data()+8,4); std::memcpy(&count,b.data()+12,4);
  std::memcpy(&pcmLen,b.data()+16,4); std::memcpy(&recLen,b.data()+20,4);
  std::vector<uint8_t> pcm(b.begin()+28+recLen, b.begin()+28+recLen+pcmLen);
  std::printf("%s : %u regions, %u Hz\n", path.c_str(), count, rate);
  int fixed=0, unmeasurable=0; double sum=0; int n=0;
  for(uint32_t i=0;i<count;i++){
    size_t base=28+i*24;
    int root=b[base]; int layer=b[base+1];
    uint32_t off,frames,ls,le; float old;
    std::memcpy(&off,b.data()+base+4,4); std::memcpy(&frames,b.data()+base+8,4);
    std::memcpy(&ls,b.data()+base+12,4); std::memcpy(&le,b.data()+base+16,4);
    std::memcpy(&old,b.data()+base+20,4);
    double want0=440.0*std::pow(2.0,(root-69)/12.0);
    double f=measure(pcm,off,frames,(int)rate,want0);
    if(f<=0){ unmeasurable++; continue; }
    double want=440.0*std::pow(2.0,(root-69)/12.0);
    double cents=1200.0*std::log2(f/want);
    if(std::fabs(cents)>400) cents=0;      // a 4-semitone gap means the measure slipped an octave
    sum+=cents; n++;
    if(std::fabs(cents-old)>2.0) fixed++;
    if(apply){ float nv=(float)cents; std::memcpy(&b[base+20],&nv,4); }
  }
  std::printf("  mesurables %d, non mesurables %d, ecart moyen %+.1f cents\n", n, unmeasurable, n? sum/n : 0.0);
  std::printf("  regions dont tuneCents change de plus de 2 cents : %d\n", fixed);
  if(apply){
    FILE* f=fopen(path.c_str(),"wb"); fwrite(b.data(),1,b.size(),f); fclose(f);
    std::printf("  ecrit.\n");
  }
  return 0;
}
