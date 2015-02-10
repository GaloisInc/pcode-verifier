#include<stdio.h>
#include<stdlib.h>

unsigned char fun (char c) {
  int res = 0;
  switch (c) {
    case 'A':
    case 'a':
      res = 10;
      break;
    case 'B':
    case 'b':
      res = 11;
      break;
    case 'C':
    case 'c':
      res = 12;
      break;
    case 'D':
    case 'd':
      res = 13;
      break;
    case 'E':
    case 'e':
      res = 14;
      break;
    case 'F':
    case 'f':
      res = 15;
      break;
    default:
      res = c - '0';
      break;
  }
  return res;
}

int main() {
  int count = 5;
  char *inputs = "beaf0";
  unsigned int res = 0;
  for (int i = 0; i < count; i++) {
    res = res << 4;
    res = res | fun(inputs[i]);
  }
  printf("beaf0 = %d\n", res);
}
