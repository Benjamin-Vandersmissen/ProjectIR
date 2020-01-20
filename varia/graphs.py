import matplotlib.pyplot as plt

def main():
    x = [1,2,3]
    plt.plot(x, [251,341,380], ls='', marker='o', label="Okapi normal") #okapi normal
    plt.plot(x, [303,403,446], ls='', marker='o', label="Okapi lowercase") #okapi lower
    plt.plot(x, [178,266,320], ls='', marker='o', label="TF-IDF normal") #TF-IDF normal
    plt.plot(x, [221,323,372], ls='', marker='o', label="TF-IDF lowercase") #TF-IDF lower
    plt.plot(x, [262,340,391], ls='', marker='o', label="Language Model normal") #LM model normal
    plt.plot(x, [318,414,457], ls='', marker='o', label="Language Model lowercase") #LM model lower
                        
    plt.xticks(x, ["10","50","100"])
    plt.xlabel("k")
    plt.ylabel("documents in top-k")
    plt.legend()
    plt.title("Evaluation for retrieval on single field")
    plt.xlim(0.9,3.1)
    
    plt.show()


if __name__ == '__main__':
    main()
