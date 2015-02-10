#include <stdio.h>

int fact(int n) {
  if (n <= 1) return 1;
  return n * fact (n-1);
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
