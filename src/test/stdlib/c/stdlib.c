#include <unistd.h>


void print(int i){

    int maxsize = 10;

	char msg[maxsize];
    msg[maxsize-1]='\n';

	int counter = maxsize-2;

	while (1){
	    msg[counter] = '0' + (i%10);
	    i /=10;
	    if (i<=0){
	        break;
	    }
	    counter--;
	}

	write(STDOUT_FILENO,&msg[counter],maxsize-counter);
}
