from tests.test_compile_and_run import run_program_text


def test_single_struct_in_frame():
    program = """
    
    struct type1:
      member1:byte
      member2:byte
    
    def main():
      a:type1
      
      a.member2=5
      a.member1=a.member2+3
      
      print(a.member1)
      print(a.member2)
     
    """
    run_program_text(program, 8, 5)
