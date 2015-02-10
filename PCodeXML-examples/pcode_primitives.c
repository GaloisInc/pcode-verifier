#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int int_add(int x, int y) { return x + y; }
int int_sub(int x, int y) { return x - y; }
int int_mul(int x, int y) { return x * y; }

int indirect_deref(int* x, unsigned i) { return x[i]; }
void indirect_assign(int* x, unsigned i, int v) { x[i] =  v; }

struct pair {
  int x;
  int y;
};

int struct_deref(struct pair* p) { return p->y; }
void struct_assign(struct pair* p, int v) { p->y = v; }

void malloc_and_free(int sz) {
  int* x = malloc(sz * sizeof(int));
  free(x);
}

void* alloc_memcpy(const void *restrict src, size_t n) {
  int* dst = malloc(n);
  memcpy(dst, src, n);
  return dst;
}

int sum_while(int* x, size_t n) {
  int* end = x+n;
  int r = 0;
  while (x != end) {
    r += *x;
    ++x;
  }
  return r;
}

int sum_for(int* x, size_t n) {
  int r = 0;
  for (int i = 0; i != n; ++i) {
    r += x[i];
  }
  return r;
}

int mux(int c, int x, int y) {
  return c ? x : y;
}

int if_mux(int c, int x, int y) {
  if (c)
    return x;
  else
    return y;
}

int first_zero(int* x, size_t n) {
  int i;
  for (i = 0; i != n; ++i) {
    if (x[i] == 0) break;
  }
  return i;
}


void printf_int(int d) {
  printf("Number %d\n", d);
}

void printf_str(char* s) {
  printf("String %s\n", s);
}

/*
extern int global_fn(int a);

int test_global_fn(int a) {
  return global_fn(a);
}
*/

int global_var = 0;

int global_read() {
  return global_var;
}

void global_write(int v) {
  global_var = v;
}

int static_update() {
  static int s = 0;
  return s++;
}

typedef int fn(int);

fn doubler1;
fn doubler2;

int call_indirect(fn f, fn g) {
  return f(1) + g(2);
}

int doubler1(int a) {
  return a + a;
}

int doubler2(int a) {
  return a << 1;
}

// Driving the test cases...
int main() {
  int t1Good, t2Good, t3Good, t4Good, t5Good, t6Good, t7Good;
  int t1, t2, t3;

  t1 = int_add(3,4);
  t2 = int_sub(5,2);
  t3 = int_mul(3,8);
  t1Good = t1 == 7 && t2 == 3 && t3 == 24;

  int arr[3] = {1,2,3};
  t1 = indirect_deref((int *)&arr, 0);
  indirect_assign((int *)&arr, 1, t1 + 3);
  t2Good = arr[1] == 4;

  struct pair p = {5,7};
  t1 = struct_deref(&p);
  struct_assign(&p, 99);
  t3Good = p.y == 99 && t1 == 7;

  int arr2[3] = {2,1,3};
  t1 = sum_while((int *)&arr2, 3);
  t2 = sum_for((int *)&arr2, 3);
  t4Good = t1 == 6 && t2 == 6;

  t1 = mux (1, 8, 10);
  t2 = if_mux (0, 8, 10);
  t5Good = t1 == 8 && t2 == 10;

  t1 = global_read();
  global_write(11);
  t2 = global_read();
  static_update();
  t3 = static_update();
  t6Good = t1 == 0 && t2 == 11 && t3 == 1;

  t1 = call_indirect(doubler1, doubler2);
  t7Good = t1 == 6;

  if (t1Good && t2Good && t3Good && t4Good && t5Good && t6Good && t7Good) {
    printf("passed\n");
  } else {
    printf("failed\n");
  }
}
