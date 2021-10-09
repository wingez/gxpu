from tests.test_compile_and_run import run_program_text


def test_fibonacci():
    program = """
    def main():
      a=1
      b=0
      c=0
    
      counter=0
      while 10-counter:
        print(a)
        c=a+b
        b=a
        a=c
        
        counter = counter+1
    """
    run_program_text(program, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55)


def test_multiply():
    program = """
    def mul(a,b):byte
      result=0
      while b:
        result=result+a
        b=b-1
    
    def main():
      print(mul(0,10))
      print(mul(4,5))
      print(mul(10,0))
    """
    run_program_text(program, 0, 20, 0)
