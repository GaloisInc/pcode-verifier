#include <stdio.h>

// I want (hope) for there to be some unique stores, 
// and fetches after the recursive call
int fact(int n) {
  int junk = n;
  if (n <= 1) return 1;
  int ret = n * fact (n-1);
  junk += ret - 1;
  if (junk < 1) return 0;
  return ret;
}

int intSymbolic() {
  return 10;
}

void writeSymbolic(int n, int f) {
  printf("fact %d is %d\n", n, f);
}

int main() {
  int n = intSymbolic();
  int f = fact(n);
  writeSymbolic(n,f);
  return(0);
}
