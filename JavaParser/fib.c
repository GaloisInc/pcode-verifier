#include<stdio.h>
#include<stdlib.h>


int fib (int x)
{
  if (x < 1)
    return 0;
  if (x == 1)
    return 1;
  return ( fib(x-2) + fib(x-1) );
}


#define N (45)

int main ()
{
  printf("Fibonacci of %d is:  %d.\n", N, fib(N));
}
